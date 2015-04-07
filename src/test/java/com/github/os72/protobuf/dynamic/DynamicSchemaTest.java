/*
 * Copyright 2015 protobuf-dynamic developers
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.os72.protobuf.dynamic;

import java.io.FileInputStream;

import org.junit.Test;
import org.junit.Assert;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.DynamicMessage;

public class DynamicSchemaTest
{
	/**
	 * testBasic - basic usage
	 */
	@Test
	public void testBasic() throws Exception {
		log("--- testBasic ---");
		
		// Create dynamic schema
		DynamicSchema.Builder schemaBuilder = DynamicSchema.newBuilder();
		schemaBuilder.setName("PersonSchemaDynamic.proto");
		
		MessageDefinition msgDef = MessageDefinition.newBuilder("Person") // message Person
				.addField("required", "int32", "id", 1)		// required int32 id = 1
				.addField("required", "string", "name", 2)	// required string name = 2
				.addField("optional", "string", "email", 3)	// optional string email = 3
				.build();
		
		schemaBuilder.addMessageDefinition(msgDef);
		DynamicSchema schema = schemaBuilder.build();
		log(schema);
		
		// Create dynamic message from schema
		DynamicMessage.Builder msgBuilder = schema.newMessageBuilder("Person");
		Descriptor msgDesc = msgBuilder.getDescriptorForType();
		DynamicMessage msg = msgBuilder
				.setField(msgDesc.findFieldByName("id"), 1)
				.setField(msgDesc.findFieldByName("name"), "Alan Turing")
				.setField(msgDesc.findFieldByName("email"), "at@sis.gov.uk")
				.build();
		log(msg);
		
		// Create data object traditional way using generated code 
		PersonSchema.Person person = PersonSchema.Person.newBuilder()
				.setId(1)
				.setName("Alan Turing")
				.setEmail("at@sis.gov.uk")
				.build();
		
		// Should be equivalent
		Assert.assertEquals(person.toString(), msg.toString());
	}

	/**
	 * testAdvanced - nested messages, enums, default values, repeated fields
	 */
	@Test
	public void testAdvanced() throws Exception {
		log("--- testAdvanced ---");
		
		// Create dynamic schema
		DynamicSchema.Builder schemaBuilder = DynamicSchema.newBuilder();
		schemaBuilder.setName("PersonSchemaDynamic.proto");
		
		EnumDefinition enumDefPhoneType = EnumDefinition.newBuilder("PhoneType") // enum PhoneType
				.addValue("MOBILE", 0)	// MOBILE = 0
				.addValue("HOME", 1)	// HOME = 1
				.addValue("WORK", 2)	// WORK = 2
				.build();
		
		MessageDefinition msgDefPhoneNumber = MessageDefinition.newBuilder("PhoneNumber") // message PhoneNumber
				.addField("required", "string", "number", 1)			// required string number = 1
				.addField("optional", "PhoneType", "type", 2, "HOME")	// optional PhoneType type = 2 [default = HOME]
				.build();
		
		MessageDefinition msgDefPerson = MessageDefinition.newBuilder("Person") // message Person
				.addEnumDefinition(enumDefPhoneType)				// enum PhoneType (nested)
				.addMessageDefinition(msgDefPhoneNumber)			// message PhoneNumber (nested)
				.addField("required", "int32", "id", 1)				// required int32 id = 1
				.addField("required", "string", "name", 2)			// required string name = 2
				.addField("optional", "string", "email", 3)			// optional string email = 3
				.addField("repeated", "PhoneNumber", "phone", 4)	// repeated PhoneNumber phone = 4
				.build();
		
		schemaBuilder.addMessageDefinition(msgDefPerson);
		DynamicSchema schema = schemaBuilder.build();
		log(schema);
		
		// Create dynamic message from schema
		Descriptor phoneDesc = schema.getMessageDescriptor("Person.PhoneNumber");
		DynamicMessage phoneMsg1 = schema.newMessageBuilder("Person.PhoneNumber")
				.setField(phoneDesc.findFieldByName("number"), "+44-111")
				.build();
		DynamicMessage phoneMsg2 = schema.newMessageBuilder("Person.PhoneNumber")
				.setField(phoneDesc.findFieldByName("number"), "+44-222")
				.setField(phoneDesc.findFieldByName("type"), schema.getEnumValue("Person.PhoneType", "WORK"))
				.build();
		
		Descriptor personDesc = schema.getMessageDescriptor("Person");
		DynamicMessage personMsg = schema.newMessageBuilder("Person")
				.setField(personDesc.findFieldByName("id"), 1)
				.setField(personDesc.findFieldByName("name"), "Alan Turing")
				.setField(personDesc.findFieldByName("email"), "at@sis.gov.uk")
				.addRepeatedField(personDesc.findFieldByName("phone"), phoneMsg1)
				.addRepeatedField(personDesc.findFieldByName("phone"), phoneMsg2)
				.build();
		log(personMsg);
		
		phoneMsg1 = (DynamicMessage)personMsg.getRepeatedField(personDesc.findFieldByName("phone"), 0);
		phoneMsg2 = (DynamicMessage)personMsg.getRepeatedField(personDesc.findFieldByName("phone"), 1);
		
		String phoneNumber1 = (String)phoneMsg1.getField(phoneDesc.findFieldByName("number"));		
		String phoneNumber2 = (String)phoneMsg2.getField(phoneDesc.findFieldByName("number"));
		
		EnumValueDescriptor phoneType1 = (EnumValueDescriptor)phoneMsg1.getField(phoneDesc.findFieldByName("type"));
		EnumValueDescriptor phoneType2 = (EnumValueDescriptor)phoneMsg2.getField(phoneDesc.findFieldByName("type"));
		
		log(phoneNumber1 + ", " + phoneType1.getName());
		log(phoneNumber2 + ", " + phoneType2.getName());
		
		Assert.assertEquals("+44-111", phoneNumber1);
		Assert.assertEquals("HOME", phoneType1.getName()); // [default = HOME]
		
		Assert.assertEquals("+44-222", phoneNumber2);
		Assert.assertEquals("WORK", phoneType2.getName());
	}

	/**
	 * testSchemaMerge - schema merging
	 */
	@Test
	public void testSchemaMerge() throws Exception {
		log("--- testSchemaMerge ---");
		
		DynamicSchema.Builder schemaBuilder1 = DynamicSchema.newBuilder().setName("Schema1.proto").setPackage("package1");
		schemaBuilder1.addMessageDefinition(MessageDefinition.newBuilder("Msg1").build());
		
		DynamicSchema.Builder schemaBuilder2 = DynamicSchema.newBuilder().setName("Schema2.proto").setPackage("package2");
		schemaBuilder2.addMessageDefinition(MessageDefinition.newBuilder("Msg2").build());
		
		schemaBuilder1.addSchema(schemaBuilder2.build());
		DynamicSchema schema1 = schemaBuilder1.build(); 
		log(schema1);
		
		// schema1 should contain both Msg1 and Msg2
		Assert.assertNotNull(schema1.getMessageDescriptor("Msg1"));
		Assert.assertNotNull(schema1.getMessageDescriptor("Msg2"));
	}

	/**
	 * testSchemaSerialization - serialization, deserialization, protoc output parsing 
	 */
	@Test
	public void testSchemaSerialization() throws Exception {
		log("--- testSchemaSerialization ---");
		
		// deserialize
		DynamicSchema schema1 = DynamicSchema.parseFrom(new FileInputStream("src/test/resources/PersonSchema.desc"));
		log(schema1);
		
		byte[] descBuf = schema1.toByteArray(); // serialize
		DynamicSchema schema2 = DynamicSchema.parseFrom(descBuf); // deserialize
		
		// Should be equivalent
		Assert.assertEquals(schema1.toString(), schema2.toString());
	}

	static void log(Object o) {
		System.out.println(o);
	}
}
