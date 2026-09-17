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

  getRows: (db, table) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows`),
  filterRows: (db, table, column, operator, value) =>
    api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows?column=${encodeURIComponent(column)}&operator=${encodeURIComponent(operator)}&value=${encodeURIComponent(value)}`),
  insertRow: (db, table, row) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows`, { method: 'POST', body: JSON.stringify(row) }),
  updateRow: (db, table, id, row) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(row) }),
  deleteRow: (db, table, id) => api(`/databases/${encodeURIComponent(db)}/tables/${encodeURIComponent(table)}/rows/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  runQuery: (db, query) => api(`/databases/${encodeURIComponent(db)}/query`, { method: 'POST', body: JSON.stringify({ query }) }),
};

// ===========================================================
// Connection
// ===========================================================

function bindStaticEvents() {
  connectBtn.addEventListener('click', () => tryConnect(false));
  baseUrlInput.addEventListener('keydown', (e) => { if (e.key === 'Enter') tryConnect(false); });
  newDbBtn.addEventListener('click', openCreateDatabaseModal);
  deleteDbBtn.addEventListener('click', confirmDeleteDatabase);
  newTableBtn.addEventListener('click', openCreateTableModal);
  deleteTableBtn.addEventListener('click', confirmDeleteTable);
  addRowBtn.addEventListener('click', () => openRowModal(null));
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
  queryView.classList.add('hidden');
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
    queryView.classList.remove('hidden');
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
      queryView.classList.add('hidden');
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
 *  the server until the user clicks Commit (see commitChanges()). */
function openRowModal(existingRow) {
  const isEdit = !!existingRow;
  const isStagedInsert = isEdit && existingRow._status === 'inserted';
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
// Commit / rollback
//
// MiniDB itself has no transaction log — there is no server-side "commit"
// or "rollback" endpoint. This is a client-side staging area: adds, edits
// and deletes are held in memory and only sent to the API when you commit.
// Because the underlying engine writes each request independently, a
// commit is NOT atomic — if one staged change fails partway through, the
// changes before it have already been saved, and you'll get a toast per
// failure so you know exactly which ones didn't go through.
// ===========================================================

async function commitChanges() {
  const db = state.currentDb, table = state.currentTable;
  const { inserts, updates, deletes } = state.pending;
  const total = pendingCount();
  if (!total) return;

  const failures = [];

  for (const row of inserts) {
    try { await Api.insertRow(db, table, row); }
    catch (err) { failures.push(`Insert failed: ${err.message}`); }
  }
  for (const [key, changes] of Object.entries(updates)) {
    if (deletes.has(key)) continue; // superseded by a staged delete below
    try { await Api.updateRow(db, table, key, changes); }
    catch (err) { failures.push(`Update of "${key}" failed: ${err.message}`); }
  }
  for (const key of deletes) {
    try { await Api.deleteRow(db, table, key); }
    catch (err) { failures.push(`Delete of "${key}" failed: ${err.message}`); }
  }

  if (failures.length) {
    toast('error', `${failures.length} of ${total} change(s) could not be saved.`);
    failures.forEach(msg => toast('error', msg));
  } else {
    toast('success', `${total} change${total === 1 ? '' : 's'} committed.`);
  }
  await loadRows(); // reloads baseRows from the server and clears pending state
}

function rollbackChanges() {
  if (!pendingCount()) return;
  resetPending();
  renderRows();
  toast('success', 'Pending changes rolled back — nothing was sent to the server.');
}

// ===========================================================
// Query console
// ===========================================================

async function runQuery() {
  const q = queryInput.value.trim();
  if (!q) { toast('error', 'Enter a query first.'); return; }
  try {
    const results = await Api.runQuery(state.currentDb, q);
    renderQueryResults(results || []);
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
