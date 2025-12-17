package me.bechberger.jfrredact.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for $PARENT marker expansion in configuration lists.
 */
public class ParentMarkerTest {

    @Test
    public void testNoParentMarker_OverridesBehavior() {
        List<String> parent = List.of("A", "B", "C");
        List<String> child = new ArrayList<>(List.of("X", "Y"));

        List<String> result = RedactionConfig.expandParentMarkers(child, parent);

        // When no $PARENT marker, child list is returned unchanged (override behavior)
        assertSame(child, result);
        assertEquals(List.of("X", "Y"), result);
    }

    @Test
    public void testParentMarkerAtEnd_Appends() {
        List<String> parent = List.of("A", "B", "C");
        List<String> child = new ArrayList<>(List.of("X", "Y", "$PARENT"));

        List<String> result = RedactionConfig.expandParentMarkers(child, parent);

        assertNotSame(child, result);
        assertEquals(List.of("X", "Y", "A", "B", "C"), result);
    }

    @Test
    public void testParentMarkerAtBeginning_Prepends() {
        List<String> parent = List.of("A", "B", "C");
        List<String> child = new ArrayList<>(List.of("$PARENT", "X", "Y"));

        List<String> result = RedactionConfig.expandParentMarkers(child, parent);

        assertEquals(List.of("A", "B", "C", "X", "Y"), result);
    }

    @Test
    public void testParentMarkerInMiddle() {
        List<String> parent = List.of("A", "B");
        List<String> child = new ArrayList<>(List.of("X", "$PARENT", "Y"));

        List<String> result = RedactionConfig.expandParentMarkers(child, parent);

        assertEquals(List.of("X", "A", "B", "Y"), result);
    }

    @Test
    public void testOnlyParentMarker_FullInheritance() {
        List<String> parent = List.of("A", "B", "C");
        List<String> child = new ArrayList<>(List.of("$PARENT"));

        List<String> result = RedactionConfig.expandParentMarkers(child, parent);

        assertEquals(List.of("A", "B", "C"), result);
    }

    @Test
    public void testMultipleParentMarkers() {
        List<String> parent = List.of("A", "B");
        List<String> child = new ArrayList<>(List.of("$PARENT", "X", "$PARENT"));

        List<String> result = RedactionConfig.expandParentMarkers(child, parent);

        // Each $PARENT is replaced with full parent list
        assertEquals(List.of("A", "B", "X", "A", "B"), result);
    }

    @Test
    public void testNullChildList() {
        List<String> parent = List.of("A", "B");

        List<String> result = RedactionConfig.expandParentMarkers(null, parent);

        assertNull(result);
    }

    @Test
    public void testNullParentList_RemovesMarker() {
        List<String> child = new ArrayList<>(List.of("X", "$PARENT", "Y"));

        List<String> result = RedactionConfig.expandParentMarkers(child, null);

        // $PARENT is expanded to empty (parent list is null)
        assertEquals(List.of("X", "Y"), result);
    }

    @Test
    public void testEmptyParentList_RemovesMarker() {
        List<String> parent = List.of();
        List<String> child = new ArrayList<>(List.of("X", "$PARENT", "Y"));

        List<String> result = RedactionConfig.expandParentMarkers(child, parent);

        // $PARENT is expanded to empty (parent list is empty)
        assertEquals(List.of("X", "Y"), result);
    }

    @Test
    public void testEmptyChildList() {
        List<String> parent = List.of("A", "B");
        List<String> child = new ArrayList<>();

        List<String> result = RedactionConfig.expandParentMarkers(child, parent);

        // Empty child with no $PARENT returns empty (override behavior)
        assertSame(child, result);
        assertTrue(result.isEmpty());
    }
}