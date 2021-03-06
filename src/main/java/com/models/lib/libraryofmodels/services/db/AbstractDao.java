package com.models.lib.libraryofmodels.services.db;

import lombok.extern.slf4j.Slf4j;

import org.apache.logging.log4j.util.Strings;
import org.springframework.jdbc.core.SingleColumnRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractDao<T extends Persistable> implements Dao<T> {

    public static final String SELECT = "SELECT %s FROM %s";
    public static final String SELECT_WHERE = "SELECT %s FROM %s WHERE %s";
    public static final String SELECT_COUNT = "SELECT COUNT(*) FROM %s";
    public static final String SELECT_COUNT_WHERE = SELECT_COUNT + " WHERE %s";
    public static final String PAGINATION = " LIMIT %d OFFSET %d ";
    public static final String EQ_PARAMETER = "%s=:%s";
    public static final String IN_PARAMETER = "%s IN (:%s)";
    public static final String NOT_IN_PARAMETER = "%s NOT IN (:%s)";

    protected final NamedParameterJdbcTemplate jdbcTemplate;
    protected final Table<T> table;

    public AbstractDao(NamedParameterJdbcTemplate jdbcTemplate, Table<T> table) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = table;
    }

    @Override
    public List<T> get(List<String> keys) {
        String pkCol = table.pkColumns().get(0).name();
        String query = String.format(SELECT_WHERE, String.join(",", table.allCols()), table.name(), String.format(IN_PARAMETER, pkCol, pkCol));
        Map<String, Object> namedParamsMap = new HashMap<String, Object>() {{ put(pkCol, keys); }};
        return jdbcTemplate.query(query, namedParamsMap, table.rowMapper());
    }

    @Override
    public Page<T> search(DbWhereClause query) {
        Map<String, Object> namedParams = getNamedParams(query);
        String whereClause = getWhereClause(query);
        String q = Strings.isNotBlank(whereClause) ? String.format(SELECT_COUNT_WHERE, table.name(), whereClause) : String.format(SELECT_COUNT, table.name());
        Long count = jdbcTemplate.query(q, namedParams, new SingleColumnRowMapper<Long>()).get(0);
        List<T> data = new ArrayList<>();
        if (count > 0) {
            String cols = String.join(",", table.allCols());
            String selectClause = Strings.isNotBlank(whereClause) ? String.format(SELECT_WHERE, cols, table.name(), whereClause) : String.format(SELECT, cols, table.name());
            int offset = query.getOffset() != null ? query.getOffset() : (query.getPageNumber() - 1) * query.getPageSize();
            String sqlFetch = selectClause + " " + String.format(PAGINATION, query.getPageSize(), offset);
            data = jdbcTemplate.query(sqlFetch, namedParams, table.rowMapper());
        }
        return Page.<T>builder().data(data).currentPage(query.getPageNumber()).pageSize(query.getPageSize()).count(count.intValue()).build();
    }

    private String getWhereClause(DbWhereClause query) {
        return query.getConditions().stream().map(condition -> {
            if (condition.getComparator().equals(DbWhereClause.Comparator.eq)) {
                return String.format(EQ_PARAMETER, condition.getColumn().name(), condition.getColumn().name());
            } else if (condition.getComparator().equals(DbWhereClause.Comparator.in)) {
                return String.format(IN_PARAMETER, condition.getColumn().name(), condition.getColumn().name());
            } else {
                return String.format(NOT_IN_PARAMETER, condition.getColumn().name(), condition.getColumn().name());
            }
        }).collect(Collectors.joining(" AND "));
    }

    private Map<String, Object> getNamedParams(DbWhereClause query) {
        Map<String, Object> namedParams = new HashMap<>();
        query.getConditions().forEach(condition -> namedParams.put(condition.getColumn().name(), condition.getValue()));
        return namedParams;
    }
}
