package com.example.minidb.engine;

/** A parsed "&lt;column&gt; &lt;operator&gt; &lt;value&gt;" condition, already resolved to RecordStorage's internal operator codes and a typed value. */
public record WhereCondition(String column, String operator, Object value) {
}
