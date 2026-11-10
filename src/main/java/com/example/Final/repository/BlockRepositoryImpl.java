package com.example.Final.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

/**
 *
 * @author aicpq
 */
public class BlockRepositoryImpl implements BlockRepositoryJdbc {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void updateSortOrders(List<Long> blockIds) {
        String sql = "UPDATE blocks SET sort_order = ? WHERE id = ?";
        int currentOrder = 1;
        for (Long id : blockIds) {
            jdbcTemplate.update(sql, currentOrder, id);
            currentOrder++;
        }
    }
}
