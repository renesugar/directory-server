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
package org.apache.directory.server.core.authz;

import static org.apache.directory.server.core.authz.AutzIntegUtils.createAccessControlSubentry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.directory.server.core.annotations.ApplyLdifs;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.ldap.entry.Entry;
import org.apache.directory.shared.ldap.exception.LdapNoPermissionException;
import org.apache.directory.shared.ldap.name.DN;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test the lookup operation
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith ( FrameworkRunner.class )
@ApplyLdifs( {
    // Entry # 1
    "dn: cn=test,ou=system",
    "objectClass: person",
    "cn: test",
    "sn: sn_test" 
})
public class LookupAuthorizationIT extends AbstractLdapTestUnit
{
    @Before
    public void init()
    {
        AutzIntegUtils.service = service;
    }

    
    /**
     * Test a lookup( DN ) operation with the ACI subsystem enabled
     */
    @Test
    public void testLookupACIEnabled() throws Exception
    {
        service.setAccessControlEnabled( true );
        DN dn = new DN( "cn=test,ou=system" );
        
        try
        {      
            Entry entry = service.getSession().lookup( dn );
            fail();
        }
        catch ( LdapNoPermissionException lnpe )
        {
            System.out.println( lnpe.getMessage() );
        }
        
        createAccessControlSubentry( 
            "anybodySearch", 
            "{ " + 
            "  identificationTag \"searchAci\", " + 
            "  precedence 14, " +
            "  authenticationLevel none, " + 
            "  itemOrUserFirst userFirst: " +
            "  { " + 
            "    userClasses { allUsers }, " +
            "    userPermissions " +
            "    { " +
            "      { " + 
            "        protectedItems {entry, allUserAttributeTypesAndValues}, " +
            "        grantsAndDenials { grantRead, grantReturnDN, grantBrowse } " +
            "      } " +
            "    } " +
            "  } " +
            "}" );
        
        Entry entry = service.getSession().lookup( dn );
        
        assertNotNull( entry );
        
        // We should have 8 attributes
        assertEquals( 8, entry.size() ); 

        // Check that all the user attributes are present
        assertEquals( "test", entry.get( "cn" ).getString() );
        assertEquals( "sn_test", entry.get( "sn" ).getString() );
        assertTrue( entry.contains( "objectClass", "top", "person" ) );
    }
}