/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dataArtisans.flinkCascading.exec.operators;

import cascading.CascadingException;
import cascading.flow.FlowElement;
import cascading.flow.FlowException;
import cascading.flow.FlowNode;
import cascading.flow.hadoop.FlowMapper;
import cascading.flow.stream.duct.Duct;
import cascading.flow.stream.element.ElementDuct;
import cascading.pipe.Boundary;
import cascading.tuple.Tuple;
import com.dataArtisans.flinkCascading.exec.FlinkFlowProcess;
import com.dataArtisans.flinkCascading.exec.BoundaryInStage;
import com.dataArtisans.flinkCascading.exec.FlinkMapStreamGraph;
import org.apache.flink.api.common.functions.RichMapPartitionFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

/**
 *
 * Corresponds to FlowMapper
 *
 */
public class Mapper extends RichMapPartitionFunction<Tuple, Tuple> {

	private static final Logger LOG = LoggerFactory.getLogger(FlowMapper.class);

	private FlowNode flowNode;

	private FlinkMapStreamGraph streamGraph;

	private BoundaryInStage sourceStage;

	private FlinkFlowProcess currentProcess;

	public Mapper() {}

	public Mapper(FlowNode flowNode) {
		this.flowNode = flowNode;
	}

	@Override
	public void open(Configuration config) {

		try {

			currentProcess = new FlinkFlowProcess(config);

			Set<FlowElement> sources = flowNode.getSourceElements();
			if(sources.size() != 1) {
				throw new RuntimeException("FlowNode for Mapper may only have a single source");
			}
			FlowElement sourceElement = sources.iterator().next();
			if(!(sourceElement instanceof Boundary)) {
				throw new RuntimeException("Source of Mapper must be a Boundary");
			}
			Boundary source = (Boundary)sourceElement;

			streamGraph = new FlinkMapStreamGraph( currentProcess, flowNode, source );

			sourceStage = this.streamGraph.getSourceStage();

			for( Duct head : streamGraph.getHeads() )
				LOG.info( "sourcing from: " + ( (ElementDuct) head ).getFlowElement() );

			for( Duct tail : streamGraph.getTails() )
				LOG.info( "sinking to: " + ( (ElementDuct) tail ).getFlowElement() );
		}
		catch( Throwable throwable ) {

			if( throwable instanceof CascadingException) {
				throw (CascadingException) throwable;
			}

			throw new FlowException( "internal error during mapper configuration", throwable );
		}

	}

	@Override
	public void mapPartition(Iterable<Tuple> input, Collector<Tuple> output) throws Exception {

//		currentProcess.setReporter( reporter );

		this.streamGraph.setTupleCollector(output);

		streamGraph.prepare();

		long processBeginTime = System.currentTimeMillis();

//		currentProcess.increment( SliceCounters.Process_Begin_Time, processBeginTime );

		try {
			try {

				sourceStage.run( input.iterator() );
			}
			catch( OutOfMemoryError error ) {
				throw error;
			}
			catch( IOException exception ) {
//				reportIfLocal( exception );
				throw exception;
			}
			catch( Throwable throwable ) {
//				reportIfLocal( throwable );

				if( throwable instanceof CascadingException ) {
					throw (CascadingException) throwable;
				}

				throw new FlowException( "internal error during mapper execution", throwable );
			}
		}
		finally
		{
			try
			{
				streamGraph.cleanup();
			}
			finally
			{
				long processEndTime = System.currentTimeMillis();

//				currentProcess.increment( SliceCounters.Process_End_Time, processEndTime );
//				currentProcess.increment( SliceCounters.Process_Duration, processEndTime - processBeginTime );
			}
		}

	}


	@Override
	public void close() {

	}

}