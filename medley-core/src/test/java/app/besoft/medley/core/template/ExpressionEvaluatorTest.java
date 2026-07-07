package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExpressionEvaluatorTest {

    static class Ctx {
        public int count = 3;
        public String name = "Ada";
        public boolean active = true;
        public List<String> items = List.of("a", "b");
        public Inner inner = new Inner();
        public int getDoubled() { return count * 2; }
    }

    static class Inner {
        public String label = "deep";
    }

    private final ExpressionEvaluator eval = new ExpressionEvaluator(new Ctx());

    @Test
    void readsField() {
        assertEquals(3, eval.eval("count"));
        assertEquals("Ada", eval.eval("name"));
    }

    @Test
    void readsGetter() {
        assertEquals(6, eval.eval("doubled"));
    }

    @Test
    void readsNestedPath() {
        assertEquals("deep", eval.eval("inner.label"));
    }

    @Test
    void literals() {
        assertEquals(42, eval.eval("42"));
        assertEquals(Boolean.TRUE, eval.eval("true"));
        assertEquals("hi", eval.eval("'hi'"));
    }

    @Test
    void comparisons() {
        assertTrue(eval.evalBoolean("count > 0"));
        assertTrue(eval.evalBoolean("count >= 3"));
        assertFalse(eval.evalBoolean("count > 5"));
        assertTrue(eval.evalBoolean("count == 3"));
        assertTrue(eval.evalBoolean("count != 4"));
        assertTrue(eval.evalBoolean("count < 10"));
    }

    @Test
    void booleanOps() {
        assertTrue(eval.evalBoolean("active && count > 0"));
        assertFalse(eval.evalBoolean("active && count > 99"));
        assertTrue(eval.evalBoolean("count > 99 || active"));
        assertFalse(eval.evalBoolean("!active"));
    }

    @Test
    void parentheses() {
        assertTrue(eval.evalBoolean("(count > 1) && (count < 5)"));
    }

    @Test
    void stringEquality() {
        assertTrue(eval.evalBoolean("name == 'Ada'"));
        assertFalse(eval.evalBoolean("name == 'Bob'"));
    }

    @Test
    void truthiness() {
        assertTrue(eval.evalBoolean("items"));      // non-empty list
        assertTrue(eval.evalBoolean("name"));       // non-empty string
        assertTrue(eval.evalBoolean("count"));      // non-zero number
    }

    @Test
    void unknownMemberThrows() {
        assertThrows(TemplateException.class, () -> eval.eval("nope"));
    }

    @Test
    void trailingInputThrows() {
        assertThrows(TemplateException.class, () -> eval.eval("count 5"));
    }
}
