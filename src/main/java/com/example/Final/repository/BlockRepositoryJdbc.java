package com.example.Final.repository;

import java.util.List;

/**
 *
 * @author aicpq
 */
public interface BlockRepositoryJdbc {
    void updateSortOrders(List<Long> blockIds);
}
