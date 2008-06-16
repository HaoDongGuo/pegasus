/**
 *  Copyright 2007-2008 University Of Southern California
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package org.griphyn.common.catalog.transformation;

import org.griphyn.cPlanner.classes.TCMap;

import org.griphyn.cPlanner.common.LogManager;
import org.griphyn.cPlanner.common.PegasusProperties;

import org.griphyn.cPlanner.poolinfo.PoolInfoProvider;
import org.griphyn.cPlanner.poolinfo.PoolMode;

import org.griphyn.common.catalog.TransformationCatalog;
import org.griphyn.common.catalog.transformation.mapper.Staged;
import org.griphyn.common.catalog.transformation.mapper.Submit;

import org.griphyn.common.util.DynamicLoader;
import org.griphyn.common.util.Separator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This is an interface for generating valid TC maps which will be used for
 * executable staging. The interface sort of access as an accessor for Pegasus
 * to the transformation catalog. The map ends up being built as and when the
 * query for a particular lfn is made to it.
 *
 * @author Gaurang Mehta
 * @version $Revision$
 */
public abstract class Mapper {

    /**
     * The name of the package where the implementing classes reside.
     */
    public static final String PACKAGE_NAME =
        "org.griphyn.common.catalog.transformation.mapper";

    /**
     * The handle to the tranformation catalog.
     */
    protected TransformationCatalog mTCHandle;

    /**
     * The handle to the RIC.
     */
    protected PoolInfoProvider mPoolHandle;

    /**
     * Handle to Pegasus Properties
     */
    protected PegasusProperties mProps;

    /**
     * Handle to the TCMap
     */
    protected TCMap mTCMap = null;

    /**
     * Handle to the logger.
     */
    protected LogManager mLogger;

    /**
     * Loads the implementing class corresponding to the mode specified by the user
     * at runtime in the properties file.
     *
     * @param className  The name of the class that implements the mode. It is the
     *                   name of the class, not the complete name with package. That
     *                   is added by itself.
     *
     * @return Mapper
     */
    public static Mapper loadTCMapper( String className ) {

        //prepend the package name
        className = PACKAGE_NAME + "." + className;

        //try loading the class dynamically
        Mapper ss = null;
        DynamicLoader dl = new DynamicLoader( className );
        try {
            Object argList[] = new Object[0 ];
            ss = ( Mapper ) dl.instantiate( argList );
        } catch ( Exception e ) {
            System.err.println( dl.convertException( e ) );
            System.exit( 1 );
        }

        return ss;
    }

    /**
     * The private constructor.
     */
    protected Mapper() {
        mLogger = LogManager.getInstance();
        mTCHandle = TCMode.loadInstance();
        mProps = PegasusProperties.getInstance();
        mTCMap = new TCMap();
        String mPoolClass = PoolMode.getImplementingClass( mProps.getPoolMode() );
        String mPoolFile = mProps.getPoolFile();
        mPoolHandle = PoolMode.loadPoolInstance( mPoolClass, mPoolFile,
            PoolMode.SINGLETON_LOAD );

    }

    /**
     * Returns whether this instance of mapper is an instance of a Stageable
     * mapper or not.
     *
     * @return boolean
     */
    public boolean isStageableMapper(){
        return ( ( this instanceof Staged )  || ( this instanceof Submit )  );
    }

    /**
     * This method returns a Map of compute sites to List of
     * TransformationCatalogEntry objects that are valid for that site.
     *
     * @param namespace  the namespace of the transformation.
     * @param name       the name of the transformation.
     * @param version    the version of the transformation.
     * @param siteids    the sites for which you want the map.
     *
     * @return Map Key=String SiteId , Values = List of TransformationCatalogEntry
     * object. Returns null if no entries are found.
     */
    public abstract Map getSiteMap( String namespace, String name,
        String version, List siteids );

    /**
     * Returns the TCMapper Mode.
     *
     * @return String
     */
    public abstract String getMode();

    /**
     * This method returns a List of TransformationCatalog Objects valid for a
     * particular transformation and for a particular compute site
     *
     * @param namespace  the namespace of the transformation.
     * @param name       the name of the transformation.
     * @param version    the version of the transformation.
     * @param siteid     the compute site for which you want the List.
     * @return List Returns null if no entries are found.
     */
    public List getTCList( String namespace, String name, String version,
        String siteid ) {
        List siteids = new ArrayList( 1 );
        List tcentries = null;
        String lfn = Separator.combine( namespace, name, version );
        siteids.add( siteid );

        if ( getSiteMap( namespace, name, version, siteids ) != null ) {
            tcentries = mTCMap.getSiteTCEntries( lfn, siteid );
        }
        return tcentries;
    }

    /**
     * Returns a list of sites that are valid sites for a given lfn and a list of sites.
     *
     * @param namespace  the namespace of the transformation.
     * @param name       the name of the transformation.
     * @param version    the version of the transformation.
     * @param siteids    the list of sites on which the transformation is to be checked.
     *
     * @return List
     */
    public List getSiteList( String namespace, String name, String version,
        List siteids ) {
        List sites = null;
        String lfn = Separator.combine( namespace, name, version );
        if ( getSiteMap( namespace, name, version, siteids ) != null ) {
            sites = mTCMap.getSiteList( lfn, siteids );
        }
        return sites;
    }

    /**
     * Checks if a give site is valid for a given transformation.
     *
     * @param namespace  the namespace of the transformation.
     * @param name       the name of the transformation.
     * @param version    the version of the transformation.
     * @param siteid     the site that needs to be checked.
     *
     * @return boolean
     */
    public boolean isSiteValid( String namespace, String name, String version,
        String siteid ) {
        List siteids = new ArrayList( 1 );
        siteids.add( siteid );
        Map m = getSiteMap( namespace, name, version, siteids );
        return ( m == null || m.isEmpty() ) ?
            false :
            true;

    }

}
