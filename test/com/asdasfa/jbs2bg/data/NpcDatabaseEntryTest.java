package com.asdasfa.jbs2bg.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

/** Verifies that retained Data state is limited to immutable NPC Database source rows. */
class NpcDatabaseEntryTest {

	/** Preserves legacy text parsing without retaining Project assignment behavior. */
	@Test
	void npcDatabaseRowsRemainImmutableIndependentSourceValues() {
		NPC npc = new NPC("Skyrim.esm | | FemaleNord | NordRace \"Nord\" | 0001A696");

		assertEquals("Skyrim.esm", npc.getMod());
		assertEquals("Unnamed (FemaleNord)", npc.getName());
		assertEquals("FemaleNord", npc.getEditorId());
		assertEquals("NordRace", npc.getRace());
		assertEquals("1A696", npc.getFormId());
		assertTrue(Modifier.isFinal(NPC.class.getModifiers()));

		Constructor<?>[] constructors = NPC.class.getDeclaredConstructors();
		assertEquals(1, constructors.length);
		assertEquals(String.class, constructors[0].getParameterTypes()[0]);
		for (Method method : NPC.class.getDeclaredMethods()) {
			assertFalse(method.getName().startsWith("set"));
			assertFalse(method.getName().contains("SliderPreset"));
			assertFalse(method.getName().equals("toLine"));
			assertFalse(method.getName().equals("getImageFile"));
		}
		for (Field field : NPC.class.getDeclaredFields())
			assertTrue(Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()));
	}
}
