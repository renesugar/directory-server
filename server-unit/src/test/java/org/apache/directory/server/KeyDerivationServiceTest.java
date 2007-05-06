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
package org.apache.directory.server;


import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.spec.DESKeySpec;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.directory.server.core.configuration.InterceptorConfiguration;
import org.apache.directory.server.core.configuration.MutableInterceptorConfiguration;
import org.apache.directory.server.core.configuration.MutablePartitionConfiguration;
import org.apache.directory.server.core.configuration.PartitionConfiguration;
import org.apache.directory.server.kerberos.shared.crypto.encryption.EncryptionType;
import org.apache.directory.server.kerberos.shared.interceptors.KeyDerivationService;
import org.apache.directory.server.kerberos.shared.io.decoder.EncryptionKeyDecoder;
import org.apache.directory.server.kerberos.shared.messages.value.EncryptionKey;
import org.apache.directory.server.kerberos.shared.store.KerberosAttribute;
import org.apache.directory.server.unit.AbstractServerTest;
import org.apache.directory.shared.ldap.message.AttributeImpl;
import org.apache.directory.shared.ldap.message.AttributesImpl;
import org.apache.directory.shared.ldap.message.ModificationItemImpl;
import org.apache.mina.util.AvailablePortFinder;


/**
 * An {@link AbstractServerTest} testing the (@link {@link KeyDerivationService}'s
 * ability to derive Kerberos symmetric keys based on userPassword and principal
 * name and to generate random keys when the special keyword "randomKey" is used.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class KeyDerivationServiceTest extends AbstractServerTest
{
    private static final String RDN = "uid=hnelson,ou=users,dc=example,dc=com";

    private DirContext ctx = null;


    /**
     * Set up a partition for EXAMPLE.COM, add the Key Derivation interceptor, enable
     * the krb5kdc schema, and add a user principal to test authentication with.
     */
    public void setUp() throws Exception
    {
        configuration.setAllowAnonymousAccess( false );

        Attributes attrs;
        Set<PartitionConfiguration> pcfgs = new HashSet<PartitionConfiguration>();

        MutablePartitionConfiguration pcfg;

        // Add partition 'example'
        pcfg = new MutablePartitionConfiguration();
        pcfg.setName( "example" );
        pcfg.setSuffix( "dc=example,dc=com" );

        Set<Object> indexedAttrs = new HashSet<Object>();
        indexedAttrs.add( "ou" );
        indexedAttrs.add( "dc" );
        indexedAttrs.add( "objectClass" );
        pcfg.setIndexedAttributes( indexedAttrs );

        attrs = new AttributesImpl( true );
        Attribute attr = new AttributeImpl( "objectClass" );
        attr.add( "top" );
        attr.add( "domain" );
        attrs.put( attr );
        attr = new AttributeImpl( "dc" );
        attr.add( "example" );
        attrs.put( attr );
        pcfg.setContextEntry( attrs );

        pcfgs.add( pcfg );
        configuration.setPartitionConfigurations( pcfgs );

        MutableInterceptorConfiguration interceptorCfg = new MutableInterceptorConfiguration();
        List<InterceptorConfiguration> list = configuration.getInterceptorConfigurations();

        interceptorCfg.setName( KeyDerivationService.NAME );
        interceptorCfg.setInterceptor( new KeyDerivationService() );
        list.add( interceptorCfg );
        configuration.setInterceptorConfigurations( list );

        doDelete( configuration.getWorkingDirectory() );
        port = AvailablePortFinder.getNextAvailable( 1024 );
        configuration.setLdapPort( port );
        configuration.setShutdownHookEnabled( false );
        setContexts( "uid=admin,ou=system", "secret" );

        // -------------------------------------------------------------------
        // Enable the krb5kdc schema
        // -------------------------------------------------------------------

        // check if krb5kdc is disabled
        Attributes krb5kdcAttrs = schemaRoot.getAttributes( "cn=Krb5kdc" );
        boolean isKrb5KdcDisabled = false;
        if ( krb5kdcAttrs.get( "m-disabled" ) != null )
        {
            isKrb5KdcDisabled = ( ( String ) krb5kdcAttrs.get( "m-disabled" ).get() ).equalsIgnoreCase( "TRUE" );
        }

        // if krb5kdc is disabled then enable it
        if ( isKrb5KdcDisabled )
        {
            Attribute disabled = new AttributeImpl( "m-disabled" );
            ModificationItemImpl[] mods = new ModificationItemImpl[]
                { new ModificationItemImpl( DirContext.REMOVE_ATTRIBUTE, disabled ) };
            schemaRoot.modifyAttributes( "cn=Krb5kdc", mods );
        }

        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( "java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( "java.naming.provider.url", "ldap://localhost:" + port + "/dc=example,dc=com" );
        env.put( "java.naming.security.principal", "uid=admin,ou=system" );
        env.put( "java.naming.security.credentials", "secret" );
        env.put( "java.naming.security.authentication", "simple" );
        ctx = new InitialDirContext( env );

        attrs = getOrgUnitAttributes( "users" );
        DirContext users = ctx.createSubcontext( "ou=users", attrs );

        attrs = getPersonAttributes( "Nelson", "Horatio Nelson", "hnelson", "secret", "hnelson@EXAMPLE.COM" );
        users.createSubcontext( "uid=hnelson", attrs );
    }


    /**
     * Tests that the addition of an entry caused keys to be derived and added.
     * 
     * @throws NamingException
     * @throws IOException 
     */
    public void testAddDerivedKeys() throws NamingException, IOException
    {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port );

        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.SECURITY_PRINCIPAL, "uid=hnelson,ou=users,dc=example,dc=com" );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( "java.naming.ldap.attributes.binary", "krb5key" );

        DirContext ctx = new InitialDirContext( env );

        String[] attrIDs =
            { "uid", "userPassword", "krb5Key" };

        Attributes attributes = ctx.getAttributes( RDN, attrIDs );

        String uid = null;

        if ( attributes.get( "uid" ) != null )
        {
            uid = ( String ) attributes.get( "uid" ).get();
        }

        assertEquals( uid, "hnelson" );

        byte[] userPassword = null;

        if ( attributes.get( "userPassword" ) != null )
        {
            userPassword = ( byte[] ) attributes.get( "userPassword" ).get();
        }

        assertEquals( "Number of keys", 5, attributes.get( "krb5key" ).size() );

        byte[] testPasswordBytes =
            { ( byte ) 0x73, ( byte ) 0x65, ( byte ) 0x63, ( byte ) 0x72, ( byte ) 0x65, ( byte ) 0x74 };
        assertTrue( Arrays.equals( userPassword, testPasswordBytes ) );

        Attribute krb5key = attributes.get( "krb5key" );
        Map<EncryptionType, EncryptionKey> map = reconstituteKeyMap( krb5key );
        EncryptionKey encryptionKey = map.get( EncryptionType.DES_CBC_MD5 );

        byte[] testKeyBytes =
            { ( byte ) 0xF4, ( byte ) 0xA7, ( byte ) 0x13, ( byte ) 0x64, ( byte ) 0x8A, ( byte ) 0x61, ( byte ) 0xCE,
                ( byte ) 0x5B };

        assertTrue( Arrays.equals( encryptionKey.getKeyValue(), testKeyBytes ) );
        assertEquals( EncryptionType.DES_CBC_MD5, encryptionKey.getKeyType() );
    }


    /**
     * Tests that the modification on an entry caused keys to be derived and modified.
     * 
     * @throws NamingException
     * @throws IOException 
     */
    public void testModifyDerivedKeys() throws NamingException, IOException
    {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + port );

        env.put( Context.SECURITY_AUTHENTICATION, "simple" );
        env.put( Context.SECURITY_PRINCIPAL, "uid=hnelson,ou=users,dc=example,dc=com" );
        env.put( Context.SECURITY_CREDENTIALS, "secret" );
        env.put( "java.naming.ldap.attributes.binary", "krb5key" );

        DirContext ctx = new InitialDirContext( env );

        String newPrincipalName = "hnelson@EXAMPLE.COM";
        String newUserPassword = "secretsecret";

        // Modify password.
        Attributes attributes = new AttributesImpl( true );
        Attribute attr = new AttributeImpl( "userPassword", newUserPassword );
        attributes.put( attr );
        attr = new AttributeImpl( KerberosAttribute.PRINCIPAL, newPrincipalName );
        attributes.put( attr );

        DirContext person = ( DirContext ) ctx.lookup( RDN );
        person.modifyAttributes( "", DirContext.REPLACE_ATTRIBUTE, attributes );

        // Read again from directory.
        person = ( DirContext ) ctx.lookup( RDN );
        attributes = person.getAttributes( "" );

        byte[] userPassword = null;

        if ( attributes.get( "userPassword" ) != null )
        {
            userPassword = ( byte[] ) attributes.get( "userPassword" ).get();
        }

        assertEquals( "Number of keys", 5, attributes.get( "krb5key" ).size() );

        byte[] testBytes =
            { 0x73, 0x65, 0x63, 0x72, 0x65, 0x74, 0x73, 0x65, 0x63, 0x72, 0x65, 0x74 };
        assertTrue( Arrays.equals( userPassword, testBytes ) );

        Attribute krb5key = attributes.get( "krb5key" );
        Map<EncryptionType, EncryptionKey> map = reconstituteKeyMap( krb5key );
        EncryptionKey encryptionKey = map.get( EncryptionType.DES_CBC_MD5 );

        byte[] testKeyBytes =
            { ( byte ) 0x16, ( byte ) 0x4A, ( byte ) 0x6D, ( byte ) 0x89, ( byte ) 0x5D, ( byte ) 0x76, ( byte ) 0x0E,
                ( byte ) 0x23 };

        assertTrue( Arrays.equals( encryptionKey.getKeyValue(), testKeyBytes ) );
        assertEquals( EncryptionType.DES_CBC_MD5, encryptionKey.getKeyType() );
    }


    /**
     * Tests that the addition of an entry caused random keys to be derived and added.
     * 
     * @throws NamingException 
     * @throws IOException 
     * @throws InvalidKeyException 
     */
    public void testAddRandomKeys() throws NamingException, IOException, InvalidKeyException
    {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( "java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( "java.naming.provider.url", "ldap://localhost:" + port + "/ou=users,dc=example,dc=com" );
        env.put( "java.naming.security.principal", "uid=admin,ou=system" );
        env.put( "java.naming.security.credentials", "secret" );
        env.put( "java.naming.security.authentication", "simple" );
        env.put( "java.naming.ldap.attributes.binary", "krb5key" );
        ctx = new InitialDirContext( env );

        Attributes attrs = getPersonAttributes( "Quist", "Thomas Quist", "tquist", "randomKey", "tquist@EXAMPLE.COM" );
        ctx.createSubcontext( "uid=tquist", attrs );

        attrs = getPersonAttributes( "Fryer", "John Fryer", "jfryer", "randomKey", "jfryer@EXAMPLE.COM" );
        ctx.createSubcontext( "uid=jfryer", attrs );

        String[] attrIDs =
            { "uid", "userPassword", "krb5Key" };

        Attributes tquistAttrs = ctx.getAttributes( "uid=tquist", attrIDs );
        Attributes jfryerAttrs = ctx.getAttributes( "uid=jfryer", attrIDs );

        String uid = null;
        byte[] userPassword = null;

        if ( tquistAttrs.get( "uid" ) != null )
        {
            uid = ( String ) tquistAttrs.get( "uid" ).get();
        }

        assertEquals( "tquist", uid );

        if ( tquistAttrs.get( "userPassword" ) != null )
        {
            userPassword = ( byte[] ) tquistAttrs.get( "userPassword" ).get();
        }

        // Bytes for "randomKey."
        byte[] testPasswordBytes =
            { ( byte ) 0x72, ( byte ) 0x61, ( byte ) 0x6E, ( byte ) 0x64, ( byte ) 0x6F, ( byte ) 0x6D, ( byte ) 0x4B,
                ( byte ) 0x65, ( byte ) 0x79 };
        assertTrue( Arrays.equals( testPasswordBytes, userPassword ) );

        if ( jfryerAttrs.get( "uid" ) != null )
        {
            uid = ( String ) jfryerAttrs.get( "uid" ).get();
        }

        assertEquals( "jfryer", uid );

        if ( jfryerAttrs.get( "userPassword" ) != null )
        {
            userPassword = ( byte[] ) jfryerAttrs.get( "userPassword" ).get();
        }

        assertTrue( Arrays.equals( testPasswordBytes, userPassword ) );

        byte[] testKeyBytes =
            { ( byte ) 0xF4, ( byte ) 0xA7, ( byte ) 0x13, ( byte ) 0x64, ( byte ) 0x8A, ( byte ) 0x61, ( byte ) 0xCE,
                ( byte ) 0x5B };

        Attribute krb5key = tquistAttrs.get( "krb5key" );
        Map<EncryptionType, EncryptionKey> map = reconstituteKeyMap( krb5key );
        EncryptionKey encryptionKey = map.get( EncryptionType.DES_CBC_MD5 );
        byte[] tquistKey = encryptionKey.getKeyValue();

        assertEquals( EncryptionType.DES_CBC_MD5, encryptionKey.getKeyType() );

        krb5key = jfryerAttrs.get( "krb5key" );
        map = reconstituteKeyMap( krb5key );
        encryptionKey = map.get( EncryptionType.DES_CBC_MD5 );
        byte[] jfryerKey = encryptionKey.getKeyValue();

        assertEquals( EncryptionType.DES_CBC_MD5, encryptionKey.getKeyType() );

        assertEquals( "Key length", 8, tquistKey.length );
        assertEquals( "Key length", 8, jfryerKey.length );

        assertFalse( Arrays.equals( testKeyBytes, tquistKey ) );
        assertFalse( Arrays.equals( testKeyBytes, jfryerKey ) );
        assertFalse( Arrays.equals( jfryerKey, tquistKey ) );

        byte[] tquistDerivedKey =
            { ( byte ) 0xFD, ( byte ) 0x7F, ( byte ) 0x6B, ( byte ) 0x83, ( byte ) 0xA4, ( byte ) 0x76, ( byte ) 0xC1,
                ( byte ) 0xEA };
        byte[] jfryerDerivedKey =
            { ( byte ) 0xA4, ( byte ) 0x10, ( byte ) 0x3B, ( byte ) 0x49, ( byte ) 0xCE, ( byte ) 0x0B, ( byte ) 0xB5,
                ( byte ) 0x07 };

        assertFalse( Arrays.equals( tquistDerivedKey, tquistKey ) );
        assertFalse( Arrays.equals( jfryerDerivedKey, jfryerKey ) );

        assertTrue( DESKeySpec.isParityAdjusted( tquistKey, 0 ) );
        assertTrue( DESKeySpec.isParityAdjusted( jfryerKey, 0 ) );
    }


    /**
     * Tear down.
     */
    public void tearDown() throws Exception
    {
        ctx.close();
        ctx = null;
        super.tearDown();
    }


    /**
     * Convenience method for creating a person.
     */
    protected Attributes getPersonAttributes( String sn, String cn, String uid, String userPassword, String principal )
    {
        Attributes attrs = new AttributesImpl();
        Attribute ocls = new AttributeImpl( "objectClass" );
        ocls.add( "top" );
        ocls.add( "person" ); // sn $ cn
        ocls.add( "inetOrgPerson" ); // uid
        ocls.add( "krb5principal" );
        ocls.add( "krb5kdcentry" );
        attrs.put( ocls );
        attrs.put( "cn", cn );
        attrs.put( "sn", sn );
        attrs.put( "uid", uid );
        attrs.put( "userPassword", userPassword );
        attrs.put( KerberosAttribute.PRINCIPAL, principal );
        attrs.put( KerberosAttribute.VERSION, "0" );

        return attrs;
    }


    /**
     * Convenience method for creating an organizational unit.
     */
    protected Attributes getOrgUnitAttributes( String ou )
    {
        Attributes attrs = new AttributesImpl();
        Attribute ocls = new AttributeImpl( "objectClass" );
        ocls.add( "top" );
        ocls.add( "organizationalUnit" );
        attrs.put( ocls );
        attrs.put( "ou", ou );

        return attrs;
    }


    private Map<EncryptionType, EncryptionKey> reconstituteKeyMap( Attribute krb5key ) throws NamingException,
        IOException
    {
        Map<EncryptionType, EncryptionKey> map = new HashMap<EncryptionType, EncryptionKey>();

        for ( int ii = 0; ii < krb5key.size(); ii++ )
        {
            byte[] encryptionKeyBytes = ( byte[] ) krb5key.get( ii );
            EncryptionKey encryptionKey = EncryptionKeyDecoder.decode( encryptionKeyBytes );
            map.put( encryptionKey.getKeyType(), encryptionKey );
        }

        return map;
    }
}
