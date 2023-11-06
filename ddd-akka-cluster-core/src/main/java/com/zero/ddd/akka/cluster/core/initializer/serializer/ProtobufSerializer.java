package com.zero.ddd.akka.cluster.core.initializer.serializer;

import com.zero.ddd.akka.cluster.core.helper.ProtoBufSerializeUtils;

import akka.serialization.JSerializer;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time 2022-07-11 12:53:52
 * @Desc 些年若许,不负芳华.
 *
 */
public class ProtobufSerializer extends JSerializer {

	@Override
	public int identifier() {
		return 1314;
	}

	@Override
	public boolean includeManifest() {
		return true;
	}

	@Override
	public byte[] toBinary(Object o) {
		return ProtoBufSerializeUtils.serialize(o);
	}

	@Override
	public Object fromBinaryJava(
			byte[] bytes, 
			Class<?> manifest) {
		return ProtoBufSerializeUtils.deserialize(bytes, manifest);
	}

}

