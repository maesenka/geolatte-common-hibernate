/*
* This file is part of the GeoLatte project.
*
*     GeoLatte is free software: you can redistribute it and/or modify
*     it under the terms of the GNU Lesser General Public License as published by
*     the Free Software Foundation, either version 3 of the License, or
*     (at your option) any later version.
*
*     GeoLatte is distributed in the hope that it will be useful,
*     but WITHOUT ANY WARRANTY; without even the implied warranty of
*     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*     GNU Lesser General Public License for more details.
*
*     You should have received a copy of the GNU Lesser General Public License
*     along with GeoLatte.  If not, see <http://www.gnu.org/licenses/>.
*
* Copyright (C) 2010 - 2012 and Ownership of code is shared by:
* Qmino bvba - Romeinsestraat 18 - 3001 Heverlee  (http://www.qmino.com)
* Geovise bvba - Generaal Eisenhowerlei 9 - 2140 Antwerpen (http://www.geovise.com)
*/

package org.geolatte.common.automapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

class TableMetaDataReader {

    final private static Logger LOGGER = LoggerFactory.getLogger(TableMetaDataReader.class);

    final private GeometryColumnTest geomTest;

    TableMetaDataReader(GeometryColumnTest geomTest) {
        this.geomTest = geomTest;
    }

    TableMetaData read(TableConfiguration cfg, DatabaseMetaData dmd) throws TableNotFoundException, MissingIdentifierException {
        LOGGER.info("Reading metadata for table " + cfg.getTableName());
        TableMetaData metaData = new TableMetaData(cfg.getTableRef());
        readColums(cfg, dmd, metaData);
        setIdentifier(cfg, dmd, metaData);
        setGeometry(cfg, metaData);
        return metaData;
    }

    private void setIdentifier(TableConfiguration cfg, DatabaseMetaData dmd, TableMetaData cInfo) throws MissingIdentifierException {
        String configuredColumn = cfg.getIdentifierColumn();
        if (configuredColumn != null) {
            setAsIdentifier(cInfo, cfg.getIdentifierColumn());
            return;
        }
        String column = determinePrimaryKey(cfg.getTableRef(), dmd);
        if (column != null) {
            setAsIdentifier(cInfo, column);
            return;
        }
        throw new MissingIdentifierException(cfg.getTableRef().toString());
    }

    private void setGeometry(TableConfiguration cfg, TableMetaData cInfo) throws MissingIdentifierException {
        String configuredColumn = cfg.getGeometryColumn();
        if (configuredColumn != null) {
            setAsGeometry(cInfo, cfg.getIdentifierColumn());
            return;
        }
        String column = determineGeometry(cInfo);
        if (column != null) {
            setAsGeometry(cInfo, column);
        }
        return;
    }

    private String determineGeometry(TableMetaData cInfo) {
        for (ColumnMetaData columnMetaData : cInfo.getColumnMetaData()) {
            if (this.geomTest.isGeometry(columnMetaData)) return columnMetaData.getColumnName();
        }
        return null;
    }

    private String determinePrimaryKey(TableRef tableRef, DatabaseMetaData dmd) {
        String pkn = null;
        ResultSet rs = null;
        try {
            rs = dmd.getPrimaryKeys(tableRef.getCatalog(), tableRef.getSchema(), tableRef.getTableName());
            if (!rs.next()) return null;
            pkn = rs.getString("COLUMN_NAME");
            //check whether the primary key is non-composite
            if (rs.next()) return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (rs != null) rs.close();
            } catch (SQLException e) {
                //do nothing
            }
        }
        return pkn;
    }

    private void readColums(TableConfiguration cfg, DatabaseMetaData dmd, TableMetaData tableMetaData) throws TableNotFoundException {
        ResultSet rs = null;
        boolean empty = true;
        try {
            rs = dmd.getColumns(cfg.getCatalog(), cfg.getSchema(), cfg.getTableName(), null);
            while (rs.next()) {
                empty = false;
                String colName = rs.getString("COLUMN_NAME");
                String dbType = rs.getString("TYPE_NAME");
                int javaType = rs.getInt("DATA_TYPE");
                if (columnToSkip(cfg, colName)) {
                    LOGGER.info(String.format("Column %s in exclude list of configuration, so excluded.", colName));
                    continue;
                }
                addAttribute(tableMetaData, colName, dbType, javaType);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        } finally {
            try {
                if (rs != null) rs.close();
            } catch (SQLException e) {
                // do nothing
            }
        }
        if (empty) {
            throw new TableNotFoundException(cfg.getTableRef());
        }
    }

    private boolean columnToSkip(TableConfiguration cfg, String colName) {
        for (String toExclude : cfg.getExcludedColumns()) {
            if (colName.equalsIgnoreCase(toExclude)) return true;
        }
        return false;
    }

    private boolean setAsIdentifier(TableMetaData metaData, String column) {
        for (ColumnMetaData ai : metaData.getColumnMetaData()) {
            if (ai.getColumnName().equals(column)) {
                ai.setAsIdentifier(true);
                return true;
            }
        }
        LOGGER.warn("Attempted to set columns " + column + " as identifier, but no corresponding field in class found.");
        return false;
    }


    private boolean setAsGeometry(TableMetaData metaData, String column) {
        for (ColumnMetaData ai : metaData.getColumnMetaData()) {
            if (ai.getColumnName().equals(column)) {
                ai.setAsGeometry(true);
                return true;
            }
        }
        LOGGER.warn("Attempted to set columns " + column + " as geometry, but no corresponding field in class found.");
        return false;
    }


    private void addAttribute(TableMetaData metaData, String colName, String dbType, int javaType) {
        metaData.addColumnMetaData(new ColumnMetaData(colName, javaType, dbType));
    }

}
