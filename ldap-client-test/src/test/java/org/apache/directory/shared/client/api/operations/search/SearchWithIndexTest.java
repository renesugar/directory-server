package org.apache.directory.shared.client.api.operations.search;

import java.io.BufferedWriter;
import java.io.FileWriter;

import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreateIndex;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.client.api.LdapApiIntegrationUtils;
import org.apache.directory.shared.ldap.model.cursor.EntryCursor;
import org.apache.directory.shared.ldap.model.entry.DefaultEntry;
import org.apache.directory.shared.ldap.model.entry.Entry;
import org.apache.directory.shared.ldap.model.message.SearchScope;
import org.apache.directory.shared.ldap.model.name.Dn;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FrameworkRunner.class)
@CreateDS(
    name="AddPerfDS",
    partitions =
    {
        @CreatePartition(
            name = "example",
            suffix = "dc=example,dc=com",
            contextEntry = @ContextEntry( 
                entryLdif =
                    "dn: dc=example,dc=com\n" +
                    "dc: example\n" +
                    "objectClass: top\n" +
                    "objectClass: domain\n\n" ),
            indexes = 
            {
                @CreateIndex( attribute = "objectClass" ),
                @CreateIndex( attribute = "sn" ),
                @CreateIndex( attribute = "cn" ),
                @CreateIndex( attribute = "displayName" )
            } )
            
    },
    enableChangeLog = false )
@CreateLdapServer(transports =
    { 
        @CreateTransport(protocol = "LDAP"), 
        @CreateTransport(protocol = "LDAPS") 
    })
public class SearchWithIndexTest extends AbstractLdapTestUnit
{
    private LdapNetworkConnection connection;
    
    
    @Before
    public void setup() throws Exception
    {
        connection = (LdapNetworkConnection)LdapApiIntegrationUtils.getPooledAdminConnection( getLdapServer() );
    }
    
    
    @After
    public void shutdown() throws Exception
    {
        LdapApiIntegrationUtils.releasePooledAdminConnection( connection, getLdapServer() );
    }

    /**
     * Test an add operation performance
     */
    @Test
    @Ignore
    public void testAddPerf() throws Exception
    {
        Dn dn = new Dn( "cn=test,ou=system" );
        Entry entry = new DefaultEntry( getService().getSchemaManager(), dn,
            "ObjectClass: top", 
            "ObjectClass: person",
            "sn: TEST",
            "cn: test" );
    
        connection.add( entry );
        int nbIterations = 800;

        BufferedWriter out = new BufferedWriter( new FileWriter("/tmp/out.txt") );

        long t0 = System.currentTimeMillis();
        long t00 = 0L;
        long tt0 = System.currentTimeMillis();
        
        for ( int i = 0; i < nbIterations; i++ )
        {
            if ( i % 1000 == 0 )
            {
                long tt1 = System.currentTimeMillis();
    
                System.out.println( i + ", " + ( tt1 - tt0 ) );
                tt0 = tt1;
            }
    
            if ( i == 5000 )
            {
                t00 = System.currentTimeMillis();
            }
    
            dn = new Dn( "uid=" + i + ",dc=example,dc=com" );
            entry = new DefaultEntry( getService().getSchemaManager(), dn,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid", Integer.toString( i ),
                "mail: A-A-R.Awg-Rosli@acme.com",
                "title: Snr Operations Technician (D)",
                "sn: Awg-Rosli",
                "departmentNumber: SMDS - UIA/G/MMO52D",
                "cn: Awg-Rosli, Awg-Abd-Rahim SMDS-UIA/G/MMO52D",
                "description: UI - S",
                "telephoneNumber: 555-1212",
                "givenName: Awg-Abd-Rahim",
                "businessCategory: Ops MDS (Malaysia) Sdn Bhd",
                "displayName", i + "Awg-Rosli, Awg-Abd-Rahim SMDS-UIA/G/MMO52D",
                "employeeNumber: A-A-R.Awg-Rosli",
                "pwdPolicySubEntry: ads-pwdId=cproint,ou=passwordPolicies,ads-interceptorId=authenticationInterceptor,ou=interceptors,ads-directoryServiceId=default,ou=config" );
    
            connection.add( entry );
        }
    
        long t1 = System.currentTimeMillis();
    
        Long deltaWarmed = ( t1 - t00 );
        System.out.println( "Delta : " + deltaWarmed + "( " + ( ( ( nbIterations - 5000 ) * 1000 ) / deltaWarmed ) + " per s ) /" + ( t1 - t0 ) );

        Entry entry1 = null;
        Entry entry2 = null;
        Entry entry3 = null;
        
        long ns0 = System.currentTimeMillis();
        EntryCursor results = connection.search("dc=example,dc=com", "(displayName=1Awg-Rosli, Awg-Abd-Rahim SMDS-UIA/G/MMO52D)", SearchScope.SUBTREE, "*" );
        
        while ( results.next() )
        {
            entry1 = results.get();
            break;
        }
        
        results.close();
        
        long ns1 = System.currentTimeMillis();
        
        System.out.println( "Delta search : " + ( ns1 - ns0 ) );

        long ns2 = System.currentTimeMillis();
        results = connection.search("dc=example,dc=com", "(displayName=3*)", SearchScope.SUBTREE, "*" );
                
        while ( results.next() )
        {
            entry2 = results.get();
            break;
        }
        results.close();
        long ns3 = System.currentTimeMillis();
        
        System.out.println( "Delta search substring : " + ( ns3 - ns2 ) );

        long ns4 = System.currentTimeMillis();
        results = connection.search("dc=example,dc=com", "(uid=6)", SearchScope.SUBTREE, "*" );
                
        while ( results.next() )
        {
            entry3 = results.get();
            break;
        }
        
        results.close();
        long ns5 = System.currentTimeMillis();
        
        System.out.println( "Delta search no index : " + ( ns5 - ns4 ) );

        System.out.println( "Entry 1 : " + entry1 );
        System.out.println( "Entry 2 : " + entry2 );
        System.out.println( "Entry 3 : " + entry3 );
        connection.close();
    }
}