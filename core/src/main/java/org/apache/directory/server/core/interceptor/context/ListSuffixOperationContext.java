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
package org.apache.directory.server.core.interceptor.context;


import org.apache.directory.server.core.CoreSession;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.apache.directory.shared.ldap.schema.registries.Registries;


/**
 * A ListSuffix context used for Interceptors. It contains all the informations
 * needed for the ListSuffix operation, and used by all the interceptors
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class ListSuffixOperationContext extends AbstractOperationContext
{
    /**
     * Creates a new instance of ListSuffixOperationContext.
     */
    public ListSuffixOperationContext( CoreSession session )
    {
        super( session );
    }
    
    /**
     * Creates a new instance of ListSuffixOperationContext.
     *
     * @param dn The DN to get the suffix from
     */
    public ListSuffixOperationContext( CoreSession session, Registries registries, LdapDN dn )
    {
        super( session, dn );
    }
    

    /**
     * @return the operation name
     */
    public String getName()
    {
        return "ListSuffix";
    }

    
    /**
     * @see Object#toString()
     */
    public String toString()
    {
        return "ListSuffixOperationContext with DN '" + getDn().getUpName() + "'";
    }
}
