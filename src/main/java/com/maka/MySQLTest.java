package com.maka;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class MySQLTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void testConnection() {
        try {
            String result = jdbcTemplate.queryForObject("SELECT 1", String.class);
            System.out.println("数据库连接成功，返回结果：" + result);
        } catch (Exception e) {
            System.err.println("数据库连接失败：" + e.getMessage());
        }
    }
}