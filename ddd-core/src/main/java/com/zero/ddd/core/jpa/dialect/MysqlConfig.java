package com.zero.ddd.core.jpa.dialect;

import org.hibernate.dialect.MySQL5Dialect;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Nov 24, 2020 7:02:00 PM
 * @Desc 些年若许,不负芳华.
 *
 */
public class MysqlConfig extends MySQL5Dialect {
    
    @Override
    public String getTableTypeString() {
        return " ENGINE=InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_bin;" ;
    }

}
