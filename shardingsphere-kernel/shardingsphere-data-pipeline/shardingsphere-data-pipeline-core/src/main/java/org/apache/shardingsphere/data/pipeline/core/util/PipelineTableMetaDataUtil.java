/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.data.pipeline.core.util;

import lombok.SneakyThrows;
import org.apache.shardingsphere.data.pipeline.api.datasource.PipelineDataSourceWrapper;
import org.apache.shardingsphere.data.pipeline.api.datasource.config.impl.StandardPipelineDataSourceConfiguration;
import org.apache.shardingsphere.data.pipeline.api.metadata.loader.PipelineTableMetaDataLoader;
import org.apache.shardingsphere.data.pipeline.api.metadata.model.PipelineColumnMetaData;
import org.apache.shardingsphere.data.pipeline.api.metadata.model.PipelineIndexMetaData;
import org.apache.shardingsphere.data.pipeline.api.metadata.model.PipelineTableMetaData;
import org.apache.shardingsphere.data.pipeline.core.datasource.PipelineDataSourceFactory;
import org.apache.shardingsphere.data.pipeline.core.exception.job.PipelineJobCreationException;
import org.apache.shardingsphere.data.pipeline.core.metadata.loader.StandardPipelineTableMetaDataLoader;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * Pipeline table meta data util.
 */
public final class PipelineTableMetaDataUtil {
    
    /**
     * Get pipeline table meta data.
     *
     * @param schemaName schema name
     * @param tableName table name
     * @param dataSourceConfig source configuration
     * @param loader pipeline table meta data loader* @return pipeline table meta data
     * @return pipeline table meta data
     */
    @SneakyThrows(SQLException.class)
    public static PipelineTableMetaData getPipelineTableMetaData(final String schemaName, final String tableName, final StandardPipelineDataSourceConfiguration dataSourceConfig,
                                                                 final PipelineTableMetaDataLoader loader) {
        try (PipelineDataSourceWrapper dataSource = PipelineDataSourceFactory.newInstance(dataSourceConfig)) {
            return getPipelineTableMetaData(schemaName, tableName, dataSource, loader);
        }
    }
    
    /**
     * Get pipeline table meta data.
     *
     * @param schemaName schema name
     * @param tableName table name
     * @param dataSource data source
     * @param loader pipeline table meta data loader
     * @return pipeline table meta data.
     */
    public static PipelineTableMetaData getPipelineTableMetaData(final String schemaName, final String tableName, final PipelineDataSourceWrapper dataSource,
                                                                 final PipelineTableMetaDataLoader loader) {
        if (null == loader) {
            return new StandardPipelineTableMetaDataLoader(dataSource).getTableMetaData(schemaName, tableName);
        } else {
            return loader.getTableMetaData(schemaName, tableName);
        }
    }
    
    /**
     * Get unique key column, if primary key exists, return primary key, otherwise return the first unique key.
     *
     * @param schemaName schema name
     * @param tableName table name
     * @param dataSourceConfig data source config
     * @param loader pipeline table meta data loader
     * @return pipeline column meta data.
     */
    public static PipelineColumnMetaData getUniqueKeyColumn(final String schemaName, final String tableName, final StandardPipelineDataSourceConfiguration dataSourceConfig,
                                                            final StandardPipelineTableMetaDataLoader loader) {
        PipelineTableMetaData pipelineTableMetaData = getPipelineTableMetaData(schemaName, tableName, dataSourceConfig, loader);
        return mustGetAnAppropriateUniqueKeyColumn(pipelineTableMetaData, tableName);
    }
    
    /**
     * Get unique key column, if primary key exists, return primary key, otherwise return the first unique key.
     *
     * @param schemaName schema name
     * @param tableName table name
     * @param dataSource data source
     * @param loader pipeline table meta data loader
     * @return pipeline column meta data.
     */
    public static PipelineColumnMetaData getUniqueKeyColumn(final String schemaName, final String tableName, final PipelineDataSourceWrapper dataSource,
                                                            final StandardPipelineTableMetaDataLoader loader) {
        PipelineTableMetaData pipelineTableMetaData = getPipelineTableMetaData(schemaName, tableName, dataSource, loader);
        return mustGetAnAppropriateUniqueKeyColumn(pipelineTableMetaData, tableName);
    }
    
    private static PipelineColumnMetaData mustGetAnAppropriateUniqueKeyColumn(final PipelineTableMetaData tableMetaData, final String tableName) {
        if (null == tableMetaData) {
            throw new PipelineJobCreationException(String.format("Can not split range for table %s, reason: can not get table metadata ", tableName));
        }
        List<String> primaryKeys = tableMetaData.getPrimaryKeyColumns();
        if (primaryKeys.size() > 1) {
            throw new PipelineJobCreationException(String.format("Can not split range for table %s, reason: primary key is union primary", tableName));
        }
        if (1 == primaryKeys.size()) {
            return tableMetaData.getColumnMetaData(tableMetaData.getPrimaryKeyColumns().get(0));
        }
        Collection<PipelineIndexMetaData> uniqueIndexes = tableMetaData.getUniqueIndexes();
        if (uniqueIndexes.isEmpty()) {
            throw new PipelineJobCreationException(String.format("Can not split range for table %s, reason: no primary key or unique index", tableName));
        }
        if (1 == uniqueIndexes.size() && 1 == uniqueIndexes.iterator().next().getColumns().size()) {
            PipelineColumnMetaData column = uniqueIndexes.iterator().next().getColumns().get(0);
            if (!column.isNullable()) {
                return column;
            }
        }
        throw new PipelineJobCreationException(
                String.format("Can not split range for table %s, reason: table contains multiple unique index or unique index contains nullable/multiple column(s)", tableName));
    }
}
