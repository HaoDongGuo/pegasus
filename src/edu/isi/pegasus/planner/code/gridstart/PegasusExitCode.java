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


package edu.isi.pegasus.planner.code.gridstart;

import edu.isi.pegasus.common.logging.LogManager;
import edu.isi.pegasus.common.logging.LogManagerFactory;
import edu.isi.pegasus.planner.classes.Job;
import edu.isi.pegasus.planner.code.POSTScript;
import edu.isi.pegasus.planner.code.generator.condor.CondorQuoteParser;
import edu.isi.pegasus.planner.code.generator.condor.CondorQuoteParserException;
import java.io.File;
import edu.isi.pegasus.planner.common.PegasusProperties;
import edu.isi.pegasus.planner.namespace.Dagman;
import edu.isi.pegasus.planner.namespace.Pegasus;
import edu.isi.pegasus.planner.namespace.aggregator.UniqueMerge;

/**
 * The exitcode wrapper, that can parse kickstart output's and put them in the
 * database also.
 *
 * @author Karan Vahi vahi@isi.edu
 * @version $Revision$
 */

public class PegasusExitCode implements POSTScript  {

    /**
     * The arguments for pegasus-exitcode when you only want the log files to be rotated.
     */
    public static final String POSTSCRIPT_ARGUMENTS_FOR_ONLY_ROTATING_LOG_FILE = "-r $RETURN";
    
    /**
     * The SHORTNAME for this implementation.
     */
    public static final String SHORT_NAME = "pegasus-exitcode";

    /**
     * The delimiter used for delimited error and success message internally
     */
    public static final String ERR_SUCCESS_MSG_DELIMITER = UniqueMerge.DEFAULT_DELIMITER;

     /**
     * The LogManager object which is used to log all the messages.
     */
    protected LogManager mLogger;

    /**
     * The object holding all the properties pertaining to Pegasus.
     */
    protected PegasusProperties mProps;


    /**
     * The path to the exitcode client that parses the exit status of
     * the kickstart. The client is run as a postscript. It also
     * includes the option to the command since at present it is same for all.
     * It is $PEGASUS_HOME/bin/pegasus-exitcode
     */
    protected String mExitParserPath;

    /**
     * The properties that need to be passed to the postscript invocation
     * on the command line in the java format.
     */
    protected String mPostScriptProperties;

    /**
     * The submit directory where the submit files are being generated for
     * the workflow.
     */
    protected String mSubmitDir;

    
    /**
     * The default constructor.
     */
    public PegasusExitCode(){
        
    }



    /**
     * Initialize the POSTScript implementation.
     *
     * @param properties the <code>PegasusProperties</code> object containing all
     *                   the properties required by Pegasus.
     * @param path       the path to the POSTScript on the submit host.
     * @param submitDir  the submit directory where the submit file for the job
     *                   has to be generated.
     */
    public void initialize( PegasusProperties properties,
                            String path,
                            String submitDir ){
        mProps     = properties;
        mSubmitDir = submitDir;
        mLogger    = LogManagerFactory.loadSingletonInstance( properties );

        //construct the exitcode paths and arguments
        mExitParserPath       = (path == null ) ? getDefaultExitCodePath() : path;
        mPostScriptProperties = getPostScriptProperties( properties );

    }

    /**
     * Constructs the postscript that has to be invoked on the submit host
     * after the job has executed on the remote end. The postscript usually
     * works on the xml output generated by kickstart. The postscript invoked
     * is exitcode that is shipped with VDS, and can usually be found at
     * $PEGASUS_HOME/bin/exitcode.
     * <p>
     * The postscript is constructed and populated as a profile
     * in the DAGMAN namespace.
     *
     *
     * @param job  the <code>Job</code> object containing the job description
     *             of the job that has to be enabled on the grid.
     * @param key  the key for the profile that has to be inserted.
     *
     * @return boolean true if postscript was generated,else false.
     */
    public boolean construct( Job job, String key ) {
        String postscript = mExitParserPath;

        job.dagmanVariables.construct( Dagman.OUTPUT_KEY, (String)job.condorVariables.get("output"));


        StringBuffer defaultOptions = new StringBuffer();
        
        //put in the postscript properties if any
        defaultOptions.append( this.mPostScriptProperties );
        
        //check for existence of Pegasus profile key for exitcode.failuremsg and exitcode.successmsg
        try{
            String failure = (String)job.vdsNS.get( Pegasus.EXITCODE_FAILURE_MESSAGE );
            if( failure != null ){
                String[] failures = failure.split( "@" );
                for( String value : failures ){
                    defaultOptions.append( " -f " ).append( CondorQuoteParser.quote( value , true ) );
                }
            }
            String success = (String)job.vdsNS.get( Pegasus.EXITCODE_SUCCESS_MESSAGE );
            if( success != null ){
                String[] successes = success.split( "@" );
                for( String value : successes ){
                    defaultOptions.append( " -s " ).append( CondorQuoteParser.quote( value , true ) );
                }
            }
        }
        catch ( CondorQuoteParserException ex) {
                throw new RuntimeException( "Condor Quote Exception for job " + job.getID(), ex);
        }
        

        //put the extra options into the exitcode arguments
        //in the correct order.
        Object args = job.dagmanVariables.get( Dagman.POST_SCRIPT_ARGUMENTS_KEY );
        StringBuffer arguments = (args == null ) ?
                                     //only have extra options
                                     defaultOptions :
                                     //have extra options in addition to existing args
                                     new StringBuffer().append( defaultOptions )
                                                       .append( " " ).append( args );
        job.dagmanVariables.construct( Dagman.POST_SCRIPT_ARGUMENTS_KEY, arguments.toString() );


        //put in the postscript
        mLogger.log("Postscript constructed is " + postscript,
                    LogManager.DEBUG_MESSAGE_LEVEL);
        job.dagmanVariables.checkKeyInNS( key, postscript );

        return true;
    }

    

    /**
     * Returns the properties that need to be passed to the the postscript
     * invocation in the java format. It is of the form
     * "-Dprop1=value1 -Dprop2=value2 .."
     *
     * @param properties   the properties object
     *
     * @return the empty string as pegasus-exitcode does not parse properties currently
     */
    protected String getPostScriptProperties( PegasusProperties properties ){
        
        //Returns an empty string, as python version of exitcode cannot parse properties
        // file.
        return "";
        /*
        StringBuffer sb = new StringBuffer();
        appendProperty( sb,
                        "pegasus.user.properties",
                        properties.getPropertiesInSubmitDirectory( ));
        return sb.toString();
        */
    }

    /**
     * Appends a property to the StringBuffer, in the java command line format.
     *
     * @param sb    the StringBuffer to append the property to.
     * @param key   the property.
     * @param value the property value.
     */
    protected void appendProperty( StringBuffer sb, String key, String value ){
        sb.append( " ").append("-D").append( key ).append( "=" ).append( value );
    }


    
    /**
     * Returns a short textual description of the implementing class.
     *
     * @return  short textual description.
     */
    public String shortDescribe(){
        return PegasusExitCode.SHORT_NAME;
    }


    /**
     * Returns the path to exitcode that is to be used on the kickstart
     * output.
     *
     * @return the path to the exitcode script to be invoked.
     */
    public String getDefaultExitCodePath(){
        StringBuffer sb = new StringBuffer();
        sb.append( mProps.getBinDir() );
        sb.append( File.separator ).append( "pegasus-exitcode" );

        return sb.toString();
    }


}
