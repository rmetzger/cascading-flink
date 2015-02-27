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

import cascading.flow.planner.Scope;
import cascading.operation.ConcreteCall;
import cascading.operation.Function;
import cascading.pipe.Each;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;
import cascading.tuple.util.TupleBuilder;
import com.dataArtisans.flinkCascading.exec.FlinkCollector;
import com.dataArtisans.flinkCascading.exec.FlinkFlowProcess;
import com.dataArtisans.flinkCascading.exec.TupleBuilderBuilder;
import org.apache.flink.api.common.functions.RichMapPartitionFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;

/**
 * Mapper that processes a single Cascading Function
 */
public class EachFunctionMapper extends RichMapPartitionFunction<Tuple, Tuple> {

	private Each each;
	private Scope outgoingScope;
	private Scope incomingScope;

	private transient Function function;
	private TupleEntry argumentsEntry;
	private TupleBuilder argumentsBuilder;
	private TupleBuilder outgoingBuilder;
	private transient ConcreteCall call;
	private FlinkFlowProcess ffp;

	public EachFunctionMapper() {}

	public EachFunctionMapper(Each each, Scope incomingScope, Scope outgoingScope) {
		this.each = each;
		this.incomingScope = incomingScope;
		this.outgoingScope = outgoingScope;
	}

	@Override
	public void open(Configuration config) {

		this.ffp = new FlinkFlowProcess(this.getRuntimeContext());
		this.function = each.getFunction();

		call = new ConcreteCall( outgoingScope.getArgumentsDeclarator(), outgoingScope.getOperationDeclaredFields() );

		Fields argumentsSelector = outgoingScope.getArgumentsSelector();
		Fields remainderFields = outgoingScope.getRemainderPassThroughFields();
		Fields outgoingSelector = outgoingScope.getOutValuesSelector();

		argumentsEntry = new TupleEntry( outgoingScope.getArgumentsDeclarator(), true );
		argumentsBuilder = TupleBuilderBuilder.createArgumentsBuilder(
				incomingScope.getIncomingFunctionArgumentFields(), argumentsSelector);
		outgoingBuilder = TupleBuilderBuilder.createOutgoingBuilder(
				each, incomingScope.getIncomingFunctionPassThroughFields(), argumentsSelector,
				remainderFields, outgoingScope.getOperationDeclaredFields(), outgoingSelector);

		call.setArguments( argumentsEntry );

	}

	@Override
	public void mapPartition(Iterable<Tuple> tuples, Collector<Tuple> collector) throws Exception {

		FlinkCollector wrappedCollector = new FlinkCollector(collector, this.outgoingBuilder, outgoingScope.getOperationDeclaredFields() );
		call.setOutputCollector(wrappedCollector);

		this.function.prepare(ffp, call);

		for(Tuple t : tuples) {

			wrappedCollector.setInTuple(t);
			argumentsEntry.setTuple( argumentsBuilder.makeResult( t, null ) );
			function.operate(ffp, call); // adds results to collector
		}
	}


	@Override
	public void close() {

	}

}
