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


package org.griphyn.cPlanner.engine;


import org.griphyn.cPlanner.classes.ADag;
import org.griphyn.cPlanner.classes.PegasusBag;
import org.griphyn.cPlanner.classes.SubInfo;

import org.griphyn.cPlanner.code.gridstart.GridStartFactory;

import org.griphyn.cPlanner.common.LogManager;

import org.griphyn.cPlanner.namespace.VDS;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;


/**
 * This class inserts the nodes for creating the random directories on the remote
 * execution pools. This is done when the resources have already been selected
 * to execute the jobs in the Dag. It adds a make directory node at the top level
 * of the graph, and all these concat to a single dummy job before branching
 * out to the root nodes of the original/ concrete dag so far. So we end up
 * introducing a  classic X shape at the top of the graph. Hence the name
 * HourGlass.
 *
 * @author Karan Vahi
 * @author Gaurang Mehta
 *
 * @version $Revision$
 */

public class HourGlass
    extends CreateDirectory {

    /**
     * The name concatenating dummy job that ensures that Condor does not start
     * staging in before the directories are created.
     */
    public static final String DUMMY_CONCAT_JOB = "pegasus_concat";

    /**
     * Default constructor.
     *
     * @param concDag  The concrete dag so far.
     * @param bag      bag of initialization objects
     */
    public HourGlass( ADag concDag, PegasusBag bag ) {
        super( concDag, bag );
    }

    /**
     * It modifies the concrete dag passed in the constructor and adds the create
     * random directory nodes to it at the root level. These directory nodes have
     * a common child that acts as a concatenating job and ensures that Condor
     * does not start staging in the data before the directories have been added.
     * The root nodes in the unmodified dag are now chidren of this concatenating
     * dummy job.
     */
    public void addCreateDirectoryNodes() {
        Set set = this.getCreateDirSites();

        //remove the entry for the local pool
        //set.remove("local");

        String pool = null;
        String jobName = null;
        SubInfo newJob = null;
        SubInfo concatJob = null;

        //add the concat job
        if (!set.isEmpty()) {
            concatJob = makeDummyConcatJob();
            introduceRootDependencies(concatJob.jobName);
            mCurrentDag.add(concatJob);
        }

        //for each execution pool add
        //a create directory node.
        for (Iterator it = set.iterator();it.hasNext();){
            pool = (String) it.next();
            jobName = getCreateDirJobName(pool);
            newJob = makeCreateDirJob(pool, jobName);
            mCurrentDag.add(newJob);

            //add the relation to the concat job
            mLogMsg = "Adding relation " + jobName + " -> " + concatJob.jobName;
            mLogger.log(mLogMsg,LogManager.DEBUG_MESSAGE_LEVEL);
            mCurrentDag.addNewRelation(jobName, concatJob.jobName);
        }

    }

    /**
     * It traverses through the root jobs of the dag and introduces a new super
     * root node to it.
     *
     * @param newRoot   the name of the job that is the new root of the graph.
     */
    private void introduceRootDependencies(String newRoot) {
        Vector vRootNodes = mCurrentDag.getRootNodes();
        Iterator it = vRootNodes.iterator();
        String job = null;

        while (it.hasNext()) {
            job = (String) it.next();
            mCurrentDag.addNewRelation(newRoot, job);
            mLogMsg = "Adding relation " + newRoot + " -> " + job;
            mLogger.log(mLogMsg,LogManager.DEBUG_MESSAGE_LEVEL);

        }
    }

    /**
     * It creates a dummy concat job that is run at the local submit host.
     * This job should run always provided the directories were created
     * successfully.
     *
     * @return  the dummy concat job.
     */
    public SubInfo makeDummyConcatJob() {

        SubInfo newJob = new SubInfo();
        List entries = null;
        String execPath =  null;

        //jobname has the dagname and index to indicate different
        //jobs for deferred planning
        newJob.jobName = getConcatJobname();

        newJob.setTransformation( this.TRANSFORMATION_NAMESPACE,
                                  this.TRANSFORMATION_NAME,
                                  this.TRANSFORMATION_VERSION );
        newJob.setDerivation( this.DERIVATION_NAMESPACE,
                              this.DERIVATION_NAME,
                              this.DERIVATION_VERSION );

        newJob.condorUniverse = Engine.REGISTRATION_UNIVERSE;
        //the noop job does not get run by condor
        //even if it does, giving it the maximum
        //possible chance
        newJob.executable = "/bin/true";

        //construct noop keys
        newJob.executionPool = "local";
        newJob.jobClass = SubInfo.CREATE_DIR_JOB;
        construct(newJob,"noop_job","true");
        construct(newJob,"noop_job_exit_code","0");

        //we do not want the job to be launched
        //by kickstart, as the job is not run actually
        newJob.vdsNS.checkKeyInNS( VDS.GRIDSTART_KEY,
                                   GridStartFactory.GRIDSTART_SHORT_NAMES[GridStartFactory.NO_GRIDSTART_INDEX] );

        return newJob;

    }

    /**
     * Returns the name of the concat job
     *
     * @return name
     */
    protected String getConcatJobname(){
        StringBuffer sb = new StringBuffer();

        sb.append( mCurrentDag.dagInfo.nameOfADag ).append( "_" ).
           append( mCurrentDag.dagInfo.index ).append( "_" );

        //append the job prefix if specified in options at runtime
        if ( mJobPrefix != null ) { sb.append( mJobPrefix ); }

        sb.append( this.DUMMY_CONCAT_JOB );

        return sb.toString();
    }

    /**
     * Constructs a condor variable in the condor profile namespace
     * associated with the job. Overrides any preexisting key values.
     *
     * @param job   contains the job description.
     * @param key   the key of the profile.
     * @param value the associated value.
     */
    private void construct(SubInfo job, String key, String value){
        job.condorVariables.checkKeyInNS(key,value);
    }

}
