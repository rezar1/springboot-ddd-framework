package com.zero.ddd.akka.cluster.core.initializer.serializer;

import java.io.IOException;
import java.nio.ByteBuffer;

import akka.actor.ExtendedActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorRefResolver;
import akka.actor.typed.javadsl.Adapter;
import io.protostuff.Input;
import io.protostuff.Output;
import io.protostuff.Pipe;
import io.protostuff.WireFormat.FieldType;
import io.protostuff.runtime.Delegate;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-11 01:15:10
 * @Desc 些年若许,不负芳华.
 *
 */
public class ActorRefDelegate implements Delegate<ActorRef<?>> {
	
	private ActorRefResolver actorRefResolver;

	public ActorRefDelegate(
			ExtendedActorSystem system) {
		this.actorRefResolver = 
				ActorRefResolver.get(
						Adapter.toTyped(system));
	}
	

	@Override
	public FieldType getFieldType() {
		return FieldType.STRING;
	}

	@Override
	public ActorRef<?> readFrom(
			Input input) throws IOException {
		String refId = 
				input.readString();
		return 
				actorRefResolver.resolveActorRef(
						refId);
	}

	@Override
	public void writeTo(
			Output output, 
			int fieldNumber, 
			ActorRef<?> actorRef, 
			boolean repeated) throws IOException {
		String serializationFormat = 
				this.actorRefResolver.toSerializationFormat(actorRef);
		output.writeString(fieldNumber, serializationFormat, repeated);
	}

	@Override
	public void transfer(
			Pipe pipe, 
			Input input, 
			Output output, 
			int fieldNumber, 
			boolean repeated) throws IOException {
		ByteBuffer readByteBuffer = 
				input.readByteBuffer();
		readByteBuffer.rewind();
		output.writeBytes(
				fieldNumber, 
				readByteBuffer, 
				repeated);
	}

	@Override
	public Class<?> typeClass() {
		return ActorRef.class;
	}

}

