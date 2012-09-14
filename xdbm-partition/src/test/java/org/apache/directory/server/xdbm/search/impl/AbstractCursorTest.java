/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.xdbm.search.impl;


import java.util.HashSet;
import java.util.Set;

import org.apache.directory.server.core.api.filtering.BaseEntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.directory.server.core.api.interceptor.context.SearchingOperationContext;
import org.apache.directory.server.core.partition.impl.btree.AbstractBTreePartition;
import org.apache.directory.server.core.partition.impl.btree.EntryCursorAdaptor;
import org.apache.directory.server.core.partition.impl.btree.IndexCursorAdaptor;
import org.apache.directory.server.xdbm.IndexEntry;
import org.apache.directory.server.xdbm.Store;
import org.apache.directory.server.xdbm.search.Evaluator;
import org.apache.directory.server.xdbm.search.PartitionSearchResult;
import org.apache.directory.shared.ldap.model.cursor.Cursor;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.filter.ExprNode;


/**
 * A class containing common method and fields for Cursor tests.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AbstractCursorTest
{
    protected EvaluatorBuilder evaluatorBuilder;
    protected CursorBuilder cursorBuilder;
    protected Store store;


    /**
     * Creates a cursor from a filter
     * 
     * @param root The filter we are using for the cursor construction
     * @return The constructed cursor
     * @throws Exception If anything went wrong
     */
    protected Cursor<Entry> buildCursor( ExprNode root ) throws Exception
    {
        Evaluator<? extends ExprNode> evaluator = evaluatorBuilder.build( root );

        PartitionSearchResult searchResult = new PartitionSearchResult();
        Set<IndexEntry<String, String>> resultSet = new HashSet<IndexEntry<String, String>>();

        Set<String> uuids = new HashSet<String>();

        long candidates = cursorBuilder.build( root, uuids );

        if ( candidates < Long.MAX_VALUE )
        {
            for ( String uuid : uuids )
            {
                IndexEntry<String, String> indexEntry = new IndexEntry<String, String>();
                indexEntry.setId( uuid );
                resultSet.add( indexEntry );
            }
        }
        else
        {
            // Full scan : use the MasterTable
            Cursor<IndexEntry<String, String>> cursor = new IndexCursorAdaptor( store.getMasterTable().cursor(), true );

            while ( cursor.next() )
            {
                IndexEntry<String, String> indexEntry = cursor.get();

                // Here, the indexEntry contains a <UUID, Entry> tuple. Convert it to <UUID, UUID> 
                IndexEntry<String, String> forwardIndexEntry = new IndexEntry<String, String>();
                forwardIndexEntry.setKey( indexEntry.getKey() );
                forwardIndexEntry.setId( indexEntry.getKey() );
                forwardIndexEntry.setEntry( indexEntry.getEntry() );

                resultSet.add( forwardIndexEntry );
            }
        }

        searchResult.setResultSet( resultSet );
        searchResult.setEvaluator( evaluator );

        SearchingOperationContext operationContext = new SearchOperationContext( null );

        return new BaseEntryFilteringCursor( new EntryCursorAdaptor( ( AbstractBTreePartition ) store, searchResult ),
            operationContext );
    }
}