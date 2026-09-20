/* ===========================================================
   MiniDB Console — frontend logic
   Talks to the Spring Boot MiniDB REST API (see /api routes).
=========================================================== */

const state = {
  baseUrl: (localStorage.getItem('minidb.baseUrl') || 'http://localhost:8080/api').replace(/\/+$/, ''),
  connected: false,
  databases: [],
  currentDb: null,
  tables: [],
  currentTable: null,
  currentTableSchema: null, // { name, columns: [{name, dataType, primaryKey}] }
  baseRows: [],       // last rows fetched from the server (committed state)
  filterActive: false,
  // 'ui' = the structured forms/staging flow below (default). 'query' = every
  // create/edit/delete action instead opens a prefilled SQL-style statement
  // that runs immediately via the query console endpoints. See runAsQuery().
  mode: localStorage.getItem('minidb.mode') === 'query' ? 'query' : 'ui',
  pending: {           // staged, uncommitted row changes — see commit()/rollback()
    inserts: [],        // array of full row objects not yet sent to the server
    updates: {},         // pkString -> { changed fields }
    deletes: new Set(),  // set of pkStrings staged for deletion
  },
};

function resetPending() {
  state.pending = { inserts: [], updates: {}, deletes: new Set() };
}

function pendingCount() {
  return state.pending.inserts.length + Object.keys(state.pending.updates).length + state.pending.deletes.size;
}

// ---------- DOM refs ----------
const el = (id) => document.getElementById(id);
const baseUrlInput   = el('baseUrl');
const connectBtn     = el('connectBtn');
const connDot        = el('connDot');
const connText       = el('connText');
const dbList         = el('dbList');
const newDbBtn       = el('newDbBtn');
const modeUiBtn      = el('modeUiBtn');
const modeQueryBtn   = el('modeQueryBtn');

const emptyState     = el('emptyState');
const dbView         = el('dbView');
const dbPathLabel    = el('dbPathLabel');
const dbNameHeading  = el('dbNameHeading');
const newTableBtn    = el('newTableBtn');
const deleteDbBtn    = el('deleteDbBtn');
const tableTabs      = el('tableTabs');

const tableView       = el('tableView');
const tableNameHeading= el('tableNameHeading');
const schemaChips     = el('schemaChips');
const addRowBtn       = el('addRowBtn');
const alterTableBtn   = el('alterTableBtn');
const deleteTableBtn  = el('deleteTableBtn');

const filterColumn   = el('filterColumn');
const filterOperator = el('filterOperator');
const filterValue    = el('filterValue');
const applyFilterBtn = el('applyFilterBtn');
const clearFilterBtn = el('clearFilterBtn');
const rowCount       = el('rowCount');

const rowsHead       = el('rowsHead');
const rowsBody       = el('rowsBody');
const rowsEmptyHint  = el('rowsEmptyHint');

const pendingBar      = el('pendingBar');
const pendingLabel    = el('pendingLabel');
const commitBtn       = el('commitBtn');
const rollbackBtn     = el('rollbackBtn');

const queryView      = el('queryView');
const queryScopeLabel = el('queryScopeLabel');
const queryInput     = el('queryInput');
const runQueryBtn    = el('runQueryBtn');
const queryHead      = el('queryHead');
const queryBody      = el('queryBody');
const queryEmptyHint = el('queryEmptyHint');

const toastStack     = el('toastStack');

const modalRoot   = el('modalRoot');
const modalBackdrop = el('modalBackdrop');
const modalTitle  = el('modalTitle');
const modalBody   = el('modalBody');
const modalClose  = el('modalClose');

// ---------- Init ----------
baseUrlInput.value = state.baseUrl;
bindStaticEvents();
tryConnect(true);

// ===========================================================
// API layer
// ===========================================================

async function api(path, options = {}) {
  const url = state.baseUrl + path;
  let res;
  try {
    res = await fetch(url, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
  } catch (networkErr) {
    setConnected(false);
    throw new Error(`Could not reach ${state.baseUrl}. Is the MiniDB server running?`);
  }

  let body = null;
  const text = await res.text();
  if (text) {
    try { body = JSON.parse(text); } catch { body = text; }
  }

  if (!res.ok) {
    // The server responded, so the connection itself is fine — this is an
    // application-level error (bad request, not found, etc), not a dropped
    // connection, so we deliberately do NOT flip the status dot here.
    setConnected(true);
    const message = (body && typeof body === 'object' && body.message) ? body.message : (typeof body === 'string' ? body : `Request failed (${res.status})`);
    throw new Error(message);
  }
  setConnected(true);
  return body;
}

const Api = {
  listDatabases: () => api('/databases'),
  createDatabase: (name) => api('/databases', { method: 'POST', body: JSON.stringify({ name }) }),
  deleteDatabase: (name) => api(`/databases/${encodeURIComponent(name)}`, { method: 'DELETE' }),

  listTables: (db) => api(`/databases/${encodeURIComponent(db)}/tables`),
  getTable: (db, table) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}`),
  createTable: (db, payload) => api(`/databases/${encodeURIComponent(db)}/tables`, { method: 'POST', body: JSON.stringify(payload) }),
  deleteTable: (db, table) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}`, { method: 'DELETE' }),
  alterTable: (db, table, payload) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}`, { method: 'PATCH', body: JSON.stringify(payload) }),

  getRows: (db, table) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows`),
  filterRows: (db, table, column, operator, value) =>
    api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows?column=${encodeURIComponent(column)}&operator=${encodeURIComponent(operator)}&value=${encodeURIComponent(value)}`),
  insertRow: (db, table, row, txId) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows${txQuery(txId)}`, { method: 'POST', body: JSON.stringify(row) }),
  updateRow: (db, table, id, row, txId) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows/${encodeURIComponent(id)}${txQuery(txId)}`, { method: 'PUT', body: JSON.stringify(row) }),
  deleteRow: (db, table, id, txId) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows/${encodeURIComponent(id)}${txQuery(txId)}`, { method: 'DELETE' }),

  runQuery: (db, query) => api(`/databases/${encodeURIComponent(db)}/query`, { method: 'POST', body: JSON.stringify({ query }) }),

  // Real, server-side transactions — see commitChanges()/rollbackChanges().
  beginTransaction: (db, table) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/transactions`, { method: 'POST' }),
  commitTransaction: (db, table, txId) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/transactions/${encodeURIComponent(txId)}/commit`, { method: 'POST' }),
  rollbackTransaction: (db, table, txId) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/transactions/${encodeURIComponent(txId)}/rollback`, { method: 'POST' }),

  // Query mode: full SQL-text statements, executed immediately (no staging).
  // runGlobalQuery is for statements not scoped to an existing database
  // (CREATE DATABASE / DROP DATABASE); runQuery (above) handles everything
  // else once a database exists.
  runGlobalQuery: (query) => api(`/query`, { method: 'POST', body: JSON.stringify({ query }) }),
};

function txQuery(txId) {
  return txId ? `?transactionId=${encodeURIComponent(txId)}` : '';
}

// ===========================================================
// Connection
// ===========================================================

function bindStaticEvents() {
  connectBtn.addEventListener('click', () => tryConnect(false));
  baseUrlInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') tryConnect(false); });
  modeUiBtn.addEventListener('click', () => setMode('ui'));
  modeQueryBtn.addEventListener('click', () => setMode('query'));
  newDbBtn.addEventListener('click', () => { if (state.mode === 'query') openCreateDatabaseQuery(); else openCreateDatabaseModal(); });
  deleteDbBtn.addEventListener('click', () => { if (state.mode === 'query') openDeleteDatabaseQuery(); else confirmDeleteDatabase(); });
  newTableBtn.addEventListener('click', () => { if (state.mode === 'query') openCreateTableQuery(); else openCreateTableModal(); });
  deleteTableBtn.addEventListener('click', () => { if (state.mode === 'query') openDeleteTableQuery(); else confirmDeleteTable(); });
  addRowBtn.addEventListener('click', () => openRowModal(null));
  alterTableBtn.addEventListener('click', () => { if (state.mode === 'query') openAlterTableQuery(); else openAlterTableModal(); });
  applyFilterBtn.addEventListener('click', applyFilter);
  clearFilterBtn.addEventListener('click', clearFilter);
  commitBtn.addEventListener('click', commitChanges);
  rollbackBtn.addEventListener('click', rollbackChanges);
  runQueryBtn.addEventListener('click', runQuery);
  queryInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') runQuery(); });
  modalClose.addEventListener('click', closeModal);
  modalBackdrop.addEventListener('click', closeModal);
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModal(); });
}

/** Switches between the structured UI forms and the "type a query instead"
 *  flow. Applies to every create/edit/delete action; doesn't affect SELECT
 *  (the query console at the bottom of a table already runs raw text). */
function setMode(mode) {
  state.mode = mode;
  localStorage.setItem('minidb.mode', mode);
  modeUiBtn.classList.toggle('active', mode === 'ui');
  modeQueryBtn.classList.toggle('active', mode === 'query');
  syncQueryConsoleVisibility();
}
setMode(state.mode);

/**
 * The query console is a persistent panel, not something you only see
 * after drilling into a table -- it's visible whenever Query mode is on
 * (from the very first screen, with nothing selected yet: that's what lets
 * you run CREATE DATABASE with no database picked), and also whenever a
 * table happens to be open in UI mode, matching where it always used to
 * live. It adapts its target (global vs. this database) and its label to
 * whatever's currently selected -- see runQuery().
 */
function syncQueryConsoleVisibility() {
  const visible = state.mode === 'query' || !!state.currentTable;
  queryView.classList.toggle('hidden', !visible);
  if (!visible) return;

  if (state.currentDb) {
    queryScopeLabel.textContent = `query console — ${state.currentDb}`;
    queryInput.placeholder = state.currentTable ? `SELECT * FROM ${state.currentTable}` : 'CREATE TABLE your_table_name (id INT PRIMARY KEY, name VARCHAR)';
  } else {
    queryScopeLabel.textContent = 'query console — no database selected';
    queryInput.placeholder = 'CREATE DATABASE your_db_name';
  }
}

async function tryConnect(silent) {
  state.baseUrl = baseUrlInput.value.trim().replace(/\/+$/, '');
  localStorage.setItem('minidb.baseUrl', state.baseUrl);
  setConnected(null);
  try {
    const dbs = await Api.listDatabases();
    setConnected(true);
    state.databases = dbs || [];
    renderDatabases();
    if (!silent) toast('success', 'Connected to MiniDB server.');
  } catch (err) {
    setConnected(false);
    if (!silent) toast('error', err.message);
  }
}

function setConnected(status) {
  state.connected = status === true;
  connDot.classList.remove('ok', 'pending');
  if (status === true) { connDot.classList.add('ok'); connText.textContent = 'Connected'; }
  else if (status === false) { connText.textContent = 'Not connected'; }
  else { connDot.classList.add('pending'); connText.textContent = 'Connecting…'; }
}

// ===========================================================
// Databases
// ===========================================================

function renderDatabases() {
  dbList.innerHTML = '';
  if (!state.databases.length) {
    dbList.innerHTML = '<li class="empty-hint">No databases yet.</li>';
    return;
  }
  state.databases.forEach((name) => {
    const li = document.createElement('li');
    li.className = 'db-item' + (name === state.currentDb ? ' active' : '');
    li.innerHTML = `<span class="db-name">${escapeHtml(name)}</span>`;
    li.addEventListener('click', () => selectDatabase(name));
    dbList.appendChild(li);
  });
}

async function selectDatabase(name) {
  state.currentDb = name;
  state.currentTable = null;
  state.currentTableSchema = null;
  resetPending();
  renderDatabases();
  emptyState.classList.add('hidden');
  dbView.classList.remove('hidden');
  tableView.classList.add('hidden');
  syncQueryConsoleVisibility();
  dbPathLabel.textContent = 'database';
  dbNameHeading.textContent = name;
  await loadTables();
}

async function loadTables() {
  try {
    state.tables = await Api.listTables(state.currentDb) || [];
    renderTableTabs();
  } catch (err) {
    toast('error', err.message);
  }
}

function renderTableTabs() {
  tableTabs.innerHTML = '';
  if (!state.tables.length) {
    tableTabs.innerHTML = '<span class="empty-hint">No tables yet. Create one to get started.</span>';
    return;
  }
  state.tables.forEach((name) => {
    const btn = document.createElement('button');
    btn.className = 'table-tab' + (name === state.currentTable ? ' active' : '');
    btn.textContent = name;
    btn.addEventListener('click', () => selectTable(name));
    tableTabs.appendChild(btn);
  });
}

function openCreateDatabaseModal() {
  openModal('New database', `
    <div class="field">
      <label for="f-db-name">Database name</label>
      <input id="f-db-name" class="input" type="text" placeholder="e.g. shop_db" autofocus>
    </div>
    <div class="modal-footer">
      <button class="btn btn-secondary" id="cancelBtn">Cancel</button>
      <button class="btn btn-primary" id="submitBtn">Create database</button>
    </div>
  `);
  el('cancelBtn').addEventListener('click', closeModal);
  el('submitBtn').addEventListener('click', async () => {
    const name = el('f-db-name').value.trim();
    if (!name) { toast('error', 'Database name is required.'); return; }
    try {
      await Api.createDatabase(name);
      toast('success', `Database "${name}" created.`);
      closeModal();
      await tryConnect(true);
      selectDatabase(name);
    } catch (err) { toast('error', err.message); }
  });
}

function confirmDeleteDatabase() {
  if (!state.currentDb) return;
  const name = state.currentDb;
  openModal('Delete database', `
    <div class="modal-warning">This deletes "${escapeHtml(name)}" and every table and row inside it. This cannot be undone.</div>
    <div class="modal-footer">
      <button class="btn btn-secondary" id="cancelBtn">Cancel</button>
      <button class="btn btn-danger" id="submitBtn">Delete database</button>
    </div>
  `);
  el('cancelBtn').addEventListener('click', closeModal);
  el('submitBtn').addEventListener('click', async () => {
    try {
      await Api.deleteDatabase(name);
      toast('success', `Database "${name}" deleted.`);
      closeModal();
      state.currentDb = null;
      state.currentTable = null;
      dbView.classList.add('hidden');
      emptyState.classList.remove('hidden');
      syncQueryConsoleVisibility();
      await tryConnect(true);
    } catch (err) { toast('error', err.message); }
  });
}

// ===========================================================
// Tables
// ===========================================================

async function selectTable(name) {
  state.currentTable = name;
  resetPending();
  renderTableTabs();
  try {
    const schema = await Api.getTable(state.currentDb, name);
    state.currentTableSchema = schema;
    state.filterActive = false;
    tableView.classList.remove('hidden');
    syncQueryConsoleVisibility();
    tableNameHeading.textContent = name;
    renderSchemaChips(schema);
    renderFilterColumnOptions(schema);
    filterValue.value = '';
    queryInput.value = `SELECT * FROM ${name}`;
    await loadRows();
  } catch (err) {
    toast('error', err.message);
  }
}

function renderSchemaChips(schema) {
  schemaChips.innerHTML = '';
  (schema.columns || []).forEach((col) => {
    const chip = document.createElement('span');
    chip.className = 'chip' + (col.primaryKey ? ' pk' : '');
    chip.innerHTML = `${escapeHtml(col.name)} <span class="type">${col.dataType}</span>${col.primaryKey ? ' · key' : ''}`;
    schemaChips.appendChild(chip);
  });
}

function renderFilterColumnOptions(schema) {
  filterColumn.innerHTML = '';
  (schema.columns || []).forEach((col) => {
    const opt = document.createElement('option');
    opt.value = col.name;
    opt.textContent = col.name;
    filterColumn.appendChild(opt);
  });
}

function openCreateTableModal() {
  let colIdx = 0;
  const makeColRow = (name = '', type = 'VARCHAR', pk = false) => {
    colIdx += 1;
    const id = `col-${colIdx}`;
    return `
      <div class="col-row" data-row-id="${id}">
        <input class="input col-name" type="text" placeholder="column name" value="${escapeHtml(name)}">
        <select class="input col-type">
          ${['INT','VARCHAR','DOUBLE','BOOLEAN','DATE'].map(t => `<option value="${t}" ${t===type?'selected':''}>${t}</option>`).join('')}
        </select>
        <label class="pk-toggle"><input type="checkbox" class="col-pk" ${pk?'checked':''}> key</label>
        <button type="button" class="remove-col" title="Remove column">&times;</button>
      </div>`;
  };

  openModal('New table', `
    <div class="field">
      <label for="f-table-name">Table name</label>
      <input id="f-table-name" class="input" type="text" placeholder="e.g. users" autofocus>
    </div>
    <div class="field">
      <label>Columns</label>
      <div id="colRows">${makeColRow('id', 'INT', true)}${makeColRow('name', 'VARCHAR', false)}</div>
      <button type="button" class="btn btn-secondary btn-sm add-col-btn" id="addColBtn">+ Add column</button>
    </div>
    <div class="modal-footer">
      <button class="btn btn-secondary" id="cancelBtn">Cancel</button>
      <button class="btn btn-primary" id="submitBtn">Create table</button>
    </div>
  `);

  const colRows = el('colRows');
  colRows.addEventListener('click', (e) => {
    if (e.target.classList.contains('remove-col')) {
      const rows = colRows.querySelectorAll('.col-row');
      if (rows.length <= 1) { toast('error', 'A table needs at least one column.'); return; }
      e.target.closest('.col-row').remove();
    }
  });
  el('addColBtn').addEventListener('click', () => {
    colRows.insertAdjacentHTML('beforeend', makeColRow());
  });
  el('cancelBtn').addEventListener('click', closeModal);
  el('submitBtn').addEventListener('click', async () => {
    const tableName = el('f-table-name').value.trim();
    if (!tableName) { toast('error', 'Table name is required.'); return; }

    const rows = [...colRows.querySelectorAll('.col-row')];
    const columns = rows.map(r => ({
      name: r.querySelector('.col-name').value.trim(),
      dataType: r.querySelector('.col-type').value,
      primaryKey: r.querySelector('.col-pk').checked,
    }));
    if (columns.some(c => !c.name)) { toast('error', 'Every column needs a name.'); return; }
    if (!columns.some(c => c.primaryKey)) { toast('error', 'Mark exactly one column as the primary key.'); return; }
    if (columns.filter(c => c.primaryKey).length > 1) { toast('error', 'Only one column can be the primary key.'); return; }

    try {
      await Api.createTable(state.currentDb, { name: tableName, columns });
      toast('success', `Table "${tableName}" created.`);
      closeModal();
      await loadTables();
      selectTable(tableName);
    } catch (err) { toast('error', err.message); }
  });
}

const DATA_TYPES = ['INT', 'VARCHAR', 'DOUBLE', 'BOOLEAN', 'DATE'];

/** Add/drop/rename a column, or change one's declared type. Unlike row
 *  edits, this always runs immediately (even in UI mode) — there's no
 *  staged/commit step for schema changes. */
function openAlterTableModal() {
  if (!state.currentTable) return;
  const columns = getColumnNames();
  const typeOptions = () => DATA_TYPES.map(t => `<option value="${t}">${t}</option>`).join('');
  const columnOptions = () => columns.map(c => `<option value="${escapeHtml(c)}">${escapeHtml(c)}</option>`).join('');

  const fieldsFor = (op) => {
    if (op === 'ADD_COLUMN') {
      return `
        <div class="field"><label for="alt-col-name">New column name</label><input id="alt-col-name" class="input" type="text" placeholder="e.g. email"></div>
        <div class="field"><label for="alt-col-type">Type</label><select id="alt-col-type" class="input select">${typeOptions()}</select></div>`;
    }
    if (op === 'DROP_COLUMN') {
      return `
        <div class="field"><label for="alt-target-col">Column to drop</label><select id="alt-target-col" class="input select">${columnOptions()}</select></div>
        <p class="mode-hint">Removes this column's value from every existing row.</p>`;
    }
    if (op === 'RENAME_COLUMN') {
      return `
        <div class="field"><label for="alt-target-col">Column to rename</label><select id="alt-target-col" class="input select">${columnOptions()}</select></div>
        <div class="field"><label for="alt-new-name">New name</label><input id="alt-new-name" class="input" type="text"></div>`;
    }
    return `
      <div class="field"><label for="alt-target-col">Column to change</label><select id="alt-target-col" class="input select">${columnOptions()}</select></div>
      <div class="field"><label for="alt-col-type">New type</label><select id="alt-col-type" class="input select">${typeOptions()}</select></div>
      <p class="mode-hint">Existing row values aren't converted — only new writes are checked against the new type.</p>`;
  };

  openModal('Alter table', `
    <div class="field">
      <label for="alt-op">Operation</label>
      <select id="alt-op" class="input select">
        <option value="ADD_COLUMN">Add column</option>
        <option value="DROP_COLUMN">Drop column</option>
        <option value="RENAME_COLUMN">Rename column</option>
        <option value="MODIFY_COLUMN">Change column type</option>
      </select>
    </div>
    <div id="alt-fields">${fieldsFor('ADD_COLUMN')}</div>
    <div class="modal-footer">
      <button class="btn btn-secondary" id="cancelBtn">Cancel</button>
      <button class="btn btn-primary" id="submitBtn">Apply</button>
    </div>
  `);

  el('alt-op').addEventListener('change', (e) => { el('alt-fields').innerHTML = fieldsFor(e.target.value); });
  el('cancelBtn').addEventListener('click', closeModal);
  el('submitBtn').addEventListener('click', async () => {
    const op = el('alt-op').value;
    const payload = { operation: op };
    if (op === 'ADD_COLUMN') {
      const name = el('alt-col-name').value.trim();
      if (!name) { toast('error', 'Column name is required.'); return; }
      payload.column = { name, dataType: el('alt-col-type').value };
    } else if (op === 'DROP_COLUMN') {
      payload.columnName = el('alt-target-col').value;
    } else if (op === 'RENAME_COLUMN') {
      const newName = el('alt-new-name').value.trim();
      if (!newName) { toast('error', 'New column name is required.'); return; }
      payload.columnName = el('alt-target-col').value;
      payload.newColumnName = newName;
    } else {
      payload.columnName = el('alt-target-col').value;
      payload.dataType = el('alt-col-type').value;
    }
    try {
      await Api.alterTable(state.currentDb, state.currentTable, payload);
      toast('success', 'Table altered successfully.');
      closeModal();
      await selectTable(state.currentTable); // reloads schema + rows to match
    } catch (err) { toast('error', err.message); }
  });
}

function confirmDeleteTable() {
  if (!state.currentTable) return;
  const name = state.currentTable;
  openModal('Delete table', `
    <div class="modal-warning">This deletes "${escapeHtml(name)}" and all of its rows. This cannot be undone.</div>
    <div class="modal-footer">
      <button class="btn btn-secondary" id="cancelBtn">Cancel</button>
      <button class="btn btn-danger" id="submitBtn">Delete table</button>
    </div>
  `);
  el('cancelBtn').addEventListener('click', closeModal);
  el('submitBtn').addEventListener('click', async () => {
    try {
      await Api.deleteTable(state.currentDb, name);
      toast('success', `Table "${name}" deleted.`);
      closeModal();
      state.currentTable = null;
      state.currentTableSchema = null;
      tableView.classList.add('hidden');
      syncQueryConsoleVisibility();
      await loadTables();
    } catch (err) { toast('error', err.message); }
  });
}

// ===========================================================
// Rows
// ===========================================================

async function loadRows() {
  try {
    state.baseRows = await Api.getRows(state.currentDb, state.currentTable) || [];
    resetPending();
    renderRows();
  } catch (err) {
    toast('error', err.message);
  }
}

async function applyFilter() {
  const column = filterColumn.value;
  const operator = filterOperator.value;
  const value = filterValue.value.trim();
  if (!column || !value) { toast('error', 'Choose a column and enter a value to filter by.'); return; }
  if (pendingCount()) { toast('error', 'Commit or roll back your pending changes before filtering.'); return; }
  try {
    state.baseRows = await Api.filterRows(state.currentDb, state.currentTable, column, operator, value) || [];
    state.filterActive = true;
    renderRows();
  } catch (err) { toast('error', err.message); }
}

async function clearFilter() {
  filterValue.value = '';
  state.filterActive = false;
  await loadRows();
}

function getColumnNames() {
  return (state.currentTableSchema?.columns || []).map(c => c.name);
}

function getPrimaryKeyColumn() {
  return (state.currentTableSchema?.columns || []).find(c => c.primaryKey);
}

function rowKey(row) {
  const pk = getPrimaryKeyColumn();
  return pk ? String(row[pk.name]) : JSON.stringify(row);
}

/** Overlays staged inserts/updates/deletes on top of the last rows fetched
 *  from the server, purely for display — nothing here has reached the API. */
function computeDisplayRows() {
  const { updates, deletes, inserts } = state.pending;
  const rows = state.baseRows.map((row) => {
    const key = rowKey(row);
    if (deletes.has(key)) return { ...row, ...(updates[key] || {}), _status: 'deleted', _key: key };
    if (updates[key]) return { ...row, ...updates[key], _status: 'updated', _key: key };
    return { ...row, _status: 'clean', _key: key };
  });
  inserts.forEach((row, idx) => rows.push({ ...row, _status: 'inserted', _key: `__insert_${idx}`, _insertIdx: idx }));
  return rows;
}

function renderPendingBar() {
  const count = pendingCount();
  if (!count) { pendingBar.classList.add('hidden'); return; }
  pendingBar.classList.remove('hidden');
  pendingLabel.textContent = count === 1 ? '1 pending change' : `${count} pending changes`;
}

function renderRows() {
  const columns = getColumnNames();
  rowsHead.innerHTML = columns.map(c => `<th>${escapeHtml(c)}</th>`).join('') + '<th>actions</th>';
  rowsBody.innerHTML = '';
  renderPendingBar();

  const displayRows = computeDisplayRows();
  const baseCount = state.baseRows.length;
  rowCount.textContent = baseCount === 1 ? '1 row' : `${baseCount} rows`;
  if (state.filterActive) rowCount.textContent += ' (filtered)';

  if (!displayRows.length) {
    rowsEmptyHint.style.display = 'block';
    return;
  }
  rowsEmptyHint.style.display = 'none';

  displayRows.forEach((row) => {
    const tr = document.createElement('tr');
    if (row._status !== 'clean') tr.className = `row-${row._status}`;
    tr.innerHTML = columns.map(c => `<td>${formatCell(row[c])}</td>`).join('');

    const actionsTd = document.createElement('td');
    actionsTd.className = 'actions';

    if (row._status === 'deleted') {
      const restoreBtn = document.createElement('button');
      restoreBtn.className = 'btn btn-secondary btn-sm';
      restoreBtn.textContent = 'Restore';
      restoreBtn.addEventListener('click', () => { state.pending.deletes.delete(row._key); renderRows(); });
      actionsTd.appendChild(restoreBtn);
    } else {
      const editBtn = document.createElement('button');
      editBtn.className = 'btn btn-secondary btn-sm';
      editBtn.textContent = 'Edit';
      editBtn.addEventListener('click', () => openRowModal(row));
      actionsTd.appendChild(editBtn);

      if (row._status === 'updated') {
        const undoBtn = document.createElement('button');
        undoBtn.className = 'btn btn-secondary btn-sm';
        undoBtn.textContent = 'Undo';
        undoBtn.addEventListener('click', () => { delete state.pending.updates[row._key]; renderRows(); });
        actionsTd.appendChild(undoBtn);
      }

      const delBtn = document.createElement('button');
      delBtn.className = 'btn btn-danger-ghost btn-sm';
      delBtn.textContent = row._status === 'inserted' ? 'Remove' : 'Delete';
      delBtn.addEventListener('click', () => stageDeleteRow(row));
      actionsTd.appendChild(delBtn);
    }

    tr.appendChild(actionsTd);
    rowsBody.appendChild(tr);
  });
}

function stageDeleteRow(row) {
  if (state.mode === 'query' && row._status !== 'inserted') {
    openDeleteRowQuery(row);
    return;
  }
  if (row._status === 'inserted') {
    state.pending.inserts.splice(row._insertIdx, 1);
  } else {
    delete state.pending.updates[row._key];
    state.pending.deletes.add(row._key);
  }
  renderRows();
}

function formatCell(value) {
  if (value === null || value === undefined) return '<span class="cell-null">null</span>';
  return escapeHtml(String(value));
}

function inputForColumn(col, value) {
  const val = value === undefined || value === null ? '' : value;
  const safe = escapeHtml(String(val));
  switch (col.dataType) {
    case 'INT':
      return `<input class="input row-field" type="number" step="1" data-col="${col.name}" value="${safe}">`;
    case 'DOUBLE':
      return `<input class="input row-field" type="number" step="any" data-col="${col.name}" value="${safe}">`;
    case 'BOOLEAN':
      return `<select class="input row-field" data-col="${col.name}">
        <option value="true" ${String(val)==='true'?'selected':''}>true</option>
        <option value="false" ${String(val)==='false'?'selected':''}>false</option>
      </select>`;
    case 'DATE':
      return `<input class="input row-field" type="date" data-col="${col.name}" value="${safe}">`;
    default:
      return `<input class="input row-field" type="text" data-col="${col.name}" value="${safe}">`;
  }
}

function castValue(dataType, raw) {
  if (raw === '' || raw === undefined) return null;
  switch (dataType) {
    case 'INT': return parseInt(raw, 10);
    case 'DOUBLE': return parseFloat(raw);
    case 'BOOLEAN': return raw === 'true' || raw === true;
    default: return raw;
  }
}

/** Adding/editing a row only stages the change locally — it is not sent to
 *  the server until the user clicks Commit (see commitChanges()) — unless
 *  mode is 'query', in which case it opens a runnable INSERT/UPDATE
 *  statement instead (see openRowQueryModal). A row that's itself still an
 *  uncommitted staged insert has no server identity yet to key an UPDATE
 *  off of, so editing it always uses the staged form regardless of mode. */
function openRowModal(existingRow) {
  const isStagedInsert = !!existingRow && existingRow._status === 'inserted';
  if (state.mode === 'query' && !isStagedInsert) {
    openRowQueryModal(existingRow);
    return;
  }

  const isEdit = !!existingRow;
  const columns = state.currentTableSchema.columns;
  const pk = getPrimaryKeyColumn();

  const fields = columns.map(col => {
    // The primary key can't change on an existing server row (the update
    // endpoint keys off it), but a still-uncommitted staged insert has no
    // server identity yet, so its key stays editable.
    const disabled = isEdit && !isStagedInsert && pk && col.name === pk.name ? 'disabled' : '';
    const fieldHtml = inputForColumn(col, isEdit ? existingRow[col.name] : '');
    const withDisabled = disabled ? fieldHtml.replace('<input', '<input disabled').replace('<select', '<select disabled') : fieldHtml;
    return `<div class="field"><label>${escapeHtml(col.name)} <span style="color:var(--text-muted)">(${col.dataType}${col.primaryKey ? ', key' : ''})</span></label>${withDisabled}</div>`;
  }).join('');

  openModal(isEdit ? 'Edit row' : 'Add row', `
    ${fields}
    <div class="modal-footer">
      <button class="btn btn-secondary" id="cancelBtn">Cancel</button>
      <button class="btn btn-primary" id="submitBtn">${isEdit ? 'Stage change' : 'Stage row'}</button>
    </div>
  `);

  el('cancelBtn').addEventListener('click', closeModal);
  el('submitBtn').addEventListener('click', () => {
    const row = {};
    columns.forEach(col => {
      const input = modalBody.querySelector(`[data-col="${CSS.escape(col.name)}"]`);
      if (!input || input.disabled) { if (isEdit) row[col.name] = existingRow[col.name]; return; }
      row[col.name] = castValue(col.dataType, input.value);
    });

    if (isStagedInsert) {
      state.pending.inserts[existingRow._insertIdx] = row;
    } else if (isEdit) {
      const key = existingRow._key;
      const changed = {};
      columns.forEach(col => {
        if (!col.primaryKey && row[col.name] !== existingRow[col.name]) changed[col.name] = row[col.name];
      });
      if (Object.keys(changed).length === 0) { delete state.pending.updates[key]; }
      else { state.pending.updates[key] = { ...(state.pending.updates[key] || {}), ...changed }; }
    } else {
      if (row[pk.name] === null || row[pk.name] === '') { toast('error', `"${pk.name}" is required.`); return; }
      state.pending.inserts.push(row);
    }
    closeModal();
    renderRows();
  });
}

// ===========================================================
// Query mode -- "type it instead" alternative to the structured forms
//
// When state.mode === 'query', the create/edit/delete buttons open a
// prefilled SQL-style statement (built from the current schema/row so you
// rarely have to type column names by hand) instead of the structured
// form. Running it executes immediately via the query console endpoints --
// it does NOT go through the pending/commit staging area the UI forms use.
// ===========================================================

/** Renders a value as a literal a typed SQL statement would accept:
 *  quoted for VARCHAR/DATE, bare for everything else, NULL for empty. */
function sqlLiteral(dataType, value) {
  if (value === null || value === undefined || value === '') return 'NULL';
  if (dataType === 'VARCHAR' || dataType === 'DATE') {
    return `'${String(value).replace(/'/g, "''")}'`;
  }
  return String(value);
}

/** MiniDB requires every column on INSERT (no optional fields), so the
 *  generated template needs a real, non-null value per column, not NULL. */
function placeholderLiteral(col) {
  switch (col.dataType) {
    case 'INT': return '0';
    case 'DOUBLE': return '0.0';
    case 'BOOLEAN': return 'false';
    case 'DATE': return `'${new Date().toISOString().slice(0, 10)}'`;
    default: return "''";
  }
}

/** Opens a modal with an editable, prefilled statement and a "Run query"
 *  button. `global` picks the endpoint: true for statements with no
 *  existing-database scope (CREATE/DROP DATABASE), false for everything
 *  else (runs against state.currentDb). */
function openRunQueryModal(title, prefilledQuery, { global = false, onSuccess } = {}) {
  openModal(title, `
    <p class="mode-hint">Edit the statement if needed, then run it. This executes immediately — it does not go through the pending/commit staging area.</p>
    <textarea id="q-text" class="query-modal-textarea" spellcheck="false">${escapeHtml(prefilledQuery)}</textarea>
    <div class="modal-footer">
      <button class="btn btn-secondary" id="cancelBtn">Cancel</button>
      <button class="btn btn-primary" id="submitBtn">Run query</button>
    </div>
  `);
  el('cancelBtn').addEventListener('click', closeModal);
  el('submitBtn').addEventListener('click', async () => {
    const query = el('q-text').value.trim();
    if (!query) { toast('error', 'Enter a query first.'); return; }
    try {
      const result = global ? await Api.runGlobalQuery(query) : await Api.runQuery(state.currentDb, query);
      closeModal();
      toast('success', describeQueryResult(result));
      if (onSuccess) await onSuccess();
    } catch (err) { toast('error', err.message); }
  });
}

/** Turns a query-endpoint response (a row array for SELECT, or a message
 *  object for everything else) into one toast-friendly sentence. */
function describeQueryResult(result) {
  if (Array.isArray(result)) {
    return `Query returned ${result.length} row${result.length === 1 ? '' : 's'}.`;
  }
  if (result && typeof result === 'object') {
    const suffix = typeof result.rowsAffected === 'number'
      ? ` (${result.rowsAffected} row${result.rowsAffected === 1 ? '' : 's'} affected)`
      : '';
    return (result.message || 'Query executed.') + suffix;
  }
  return 'Query executed.';
}

function openCreateDatabaseQuery() {
  openRunQueryModal('Create database (query)', 'CREATE DATABASE your_db_name', {
    global: true,
    onSuccess: () => tryConnect(true),
  });
}

function openDeleteDatabaseQuery() {
  if (!state.currentDb) return;
  const name = state.currentDb;
  openRunQueryModal('Drop database (query)', `DROP DATABASE ${name}`, {
    global: true,
    onSuccess: async () => {
      state.currentDb = null;
      state.currentTable = null;
      dbView.classList.add('hidden');
      emptyState.classList.remove('hidden');
      syncQueryConsoleVisibility();
      await tryConnect(true);
    },
  });
}

function openCreateTableQuery() {
  if (!state.currentDb) return;
  const template = 'CREATE TABLE your_table_name (\n  id INT PRIMARY KEY,\n  name VARCHAR\n)';
  openRunQueryModal('Create table (query)', template, { onSuccess: loadTables });
}

function openDeleteTableQuery() {
  if (!state.currentTable) return;
  const name = state.currentTable;
  openRunQueryModal('Drop table (query)', `DROP TABLE ${name}`, {
    onSuccess: async () => {
      state.currentTable = null;
      state.currentTableSchema = null;
      tableView.classList.add('hidden');
      syncQueryConsoleVisibility();
      await loadTables();
    },
  });
}

function openAlterTableQuery() {
  if (!state.currentTable) return;
  const template = `ALTER TABLE ${state.currentTable} ADD COLUMN new_column VARCHAR`;
  openRunQueryModal('Alter table (query)', template, {
    onSuccess: () => selectTable(state.currentTable), // reloads schema + rows to match
  });
}

/** Add/Edit row in query mode: builds an INSERT (no existing row) or an
 *  UPDATE keyed on the primary key (existing row), prefilled with its
 *  current values so editing is usually just tweaking one or two literals. */
function openRowQueryModal(existingRow) {
  const columns = state.currentTableSchema.columns;
  const pk = getPrimaryKeyColumn();
  const table = state.currentTable;

  if (existingRow) {
    const setParts = columns.filter(c => !c.primaryKey)
      .map(c => `${c.name} = ${sqlLiteral(c.dataType, existingRow[c.name])}`);
    const query = `UPDATE ${table} SET ${setParts.join(', ')} WHERE ${pk.name} = ${sqlLiteral(pk.dataType, existingRow[pk.name])}`;
    openRunQueryModal('Edit row (query)', query, { onSuccess: loadRows });
  } else {
    const colNames = columns.map(c => c.name).join(', ');
    const values = columns.map(placeholderLiteral).join(', ');
    const query = `INSERT INTO ${table} (${colNames}) VALUES (${values})`;
    openRunQueryModal('Add row (query)', query, { onSuccess: loadRows });
  }
}

function openDeleteRowQuery(row) {
  const pk = getPrimaryKeyColumn();
  const query = `DELETE FROM ${state.currentTable} WHERE ${pk.name} = ${sqlLiteral(pk.dataType, row[pk.name])}`;
  openRunQueryModal('Delete row (query)', query, { onSuccess: loadRows });
}

// ===========================================================
// Commit / rollback
//
// Adds, edits and deletes are staged locally first (state.pending) so the
// grid feels instant and Rollback before you've committed is free — no
// server round trip needed, since nothing has been sent yet.
//
// Committing now uses MiniDB's real server-side transactions instead of
// firing each staged change at the live rows directly:
//   1. begin a transaction for this table (POST .../transactions)
//   2. replay every staged insert/update/delete against it, tagged with
//      its transactionId — these land in a private staging file, not the
//      live one
//   3. if every staged change applied cleanly, commit the transaction —
//      one atomic swap makes them all visible at once
//   4. if ANY staged change failed, roll the transaction back instead —
//      the live table is left exactly as it was, and your local pending
//      changes are kept so you can fix the problem and try again
// This is what makes commit atomic: partial failures can no longer leave
// the live table half-updated.
// ===========================================================

async function commitChanges() {
  const db = state.currentDb, table = state.currentTable;
  const { inserts, updates, deletes } = state.pending;
  const total = pendingCount();
  if (!total) return;

  commitBtn.disabled = true;
  rollbackBtn.disabled = true;

  let txId;
  try {
    const begun = await Api.beginTransaction(db, table);
    txId = begun.transactionId;
  } catch (err) {
    toast('error', `Could not start transaction: ${err.message}`);
    commitBtn.disabled = false;
    rollbackBtn.disabled = false;
    return;
  }

  const failures = [];

  for (const row of inserts) {
    try { await Api.insertRow(db, table, row, txId); }
    catch (err) { failures.push(`Insert failed: ${err.message}`); }
  }
  for (const [key, changes] of Object.entries(updates)) {
    if (deletes.has(key)) continue; // superseded by a staged delete below
    try { await Api.updateRow(db, table, key, changes, txId); }
    catch (err) { failures.push(`Update of "${key}" failed: ${err.message}`); }
  }
  for (const key of deletes) {
    try { await Api.deleteRow(db, table, key, txId); }
    catch (err) { failures.push(`Delete of "${key}" failed: ${err.message}`); }
  }

  if (failures.length) {
    try { await Api.rollbackTransaction(db, table, txId); }
    catch (err) { /* transaction will simply sit unresolved server-side */ }
    toast('error', `${failures.length} of ${total} change(s) failed — nothing was saved.`);
    failures.forEach(msg => toast('error', msg));
    commitBtn.disabled = false;
    rollbackBtn.disabled = false;
    return; // keep the staged changes so the user can fix and retry
  }

  try {
    await Api.commitTransaction(db, table, txId);
    toast('success', `${total} change${total === 1 ? '' : 's'} committed.`);
    await loadRows(); // reloads baseRows from the server and clears pending state
  } catch (err) {
    toast('error', `Changes were staged but the commit itself failed: ${err.message}`);
  } finally {
    commitBtn.disabled = false;
    rollbackBtn.disabled = false;
  }
}

function rollbackChanges() {
  if (!pendingCount()) return;
  resetPending();
  renderRows();
  toast('success', 'Pending changes rolled back — nothing was sent to the server.');
}

// ===========================================================
// Query console
//
// This box runs the full statement grammar now, not just SELECT: INSERT,
// UPDATE, DELETE, CREATE TABLE, DROP TABLE all work here too (executed
// immediately, same as the query-mode modals above). A SELECT's response
// is a row array and renders as a results table; everything else returns
// a message object, which just gets toasted and also refreshes the grid
// and table list in case it touched what's currently shown.
// ===========================================================

async function runQuery() {
  const q = queryInput.value.trim();
  if (!q) { toast('error', 'Enter a query first.'); return; }
  try {
    // No database picked yet -> only CREATE DATABASE / DROP DATABASE make
    // sense, and those run against the un-scoped endpoint (see SqlEngine's
    // executeGlobal). Once a database is selected, everything else runs
    // scoped to it, same as before.
    const result = state.currentDb ? await Api.runQuery(state.currentDb, q) : await Api.runGlobalQuery(q);
    if (Array.isArray(result)) {
      renderQueryResults(result);
    } else {
      renderQueryMessage(result);
      toast('success', describeQueryResult(result));
      // The statement may have changed rows in the currently open table,
      // added/dropped a table, or -- with no database selected -- created
      // or dropped a database entirely. Refresh whichever list applies so
      // the UI can't drift out of sync with what the query just did.
      if (state.currentDb) {
        if (state.currentTable) await loadRows();
        await loadTables();
      } else {
        await tryConnect(true); // repopulates the database list in the sidebar
      }
    }
  } catch (err) {
    toast('error', err.message);
    queryHead.innerHTML = '';
    queryBody.innerHTML = '';
    queryEmptyHint.style.display = 'block';
    queryEmptyHint.textContent = 'No results — the query above failed.';
  }
}

function renderQueryResults(rows) {
  queryHead.innerHTML = '';
  queryBody.innerHTML = '';
  if (!rows.length) {
    queryEmptyHint.style.display = 'block';
    queryEmptyHint.textContent = 'Query ran successfully, but returned no rows.';
    return;
  }
  queryEmptyHint.style.display = 'none';
  const columns = Object.keys(rows[0]);
  queryHead.innerHTML = columns.map(c => `<th>${escapeHtml(c)}</th>`).join('');
  rows.forEach(row => {
    const tr = document.createElement('tr');
    tr.innerHTML = columns.map(c => `<td>${formatCell(row[c])}</td>`).join('');
    queryBody.appendChild(tr);
  });
}

/** Shows a non-SELECT result (INSERT/UPDATE/DELETE/CREATE TABLE/DROP TABLE)
 *  in the same results area a SELECT would use, since there are no rows to
 *  list — just the message the statement returned. */
function renderQueryMessage(result) {
  queryHead.innerHTML = '';
  queryBody.innerHTML = '';
  queryEmptyHint.style.display = 'block';
  queryEmptyHint.textContent = describeQueryResult(result);
}

// ===========================================================
// Modal helpers
// ===========================================================

function openModal(title, bodyHtml) {
  modalTitle.textContent = title;
  modalBody.innerHTML = bodyHtml;
  modalRoot.classList.remove('hidden');
  const firstInput = modalBody.querySelector('input, select');
  if (firstInput) setTimeout(() => firstInput.focus(), 30);
}

function closeModal() {
  modalRoot.classList.add('hidden');
  modalBody.innerHTML = '';
}

// ===========================================================
// Toasts
// ===========================================================

function toast(type, message) {
  const t = document.createElement('div');
  t.className = `toast ${type}`;
  t.textContent = message;
  toastStack.appendChild(t);
  setTimeout(() => t.remove(), 4200);
}

// ===========================================================
// Utils
// ===========================================================

function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
