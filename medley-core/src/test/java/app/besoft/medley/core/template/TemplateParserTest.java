package app.besoft.medley.core.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TemplateParserTest {

    @Test
    void parsesSimpleElement() {
        TemplateNode.Element el = TemplateParser.parse("<div class=\"x\">hello</div>");
        assertEquals("div", el.tag());
        assertEquals("x", el.staticAttrs().get("class"));
        assertEquals(1, el.children().size());
        assertTrue(el.children().get(0) instanceof TemplateNode.Text);
    }

    @Test
    void parsesInterpolation() {
        TemplateNode.Element el = TemplateParser.parse("<span>{{ count }}</span>");
        TemplateNode child = el.children().get(0);
        assertTrue(child instanceof TemplateNode.Interpolation);
        assertEquals("count", ((TemplateNode.Interpolation) child).expr());
    }

    @Test
    void parsesBoundAttribute() {
        TemplateNode.Element el = TemplateParser.parse("<a :href=\"url\">go</a>");
        assertEquals("url", el.boundAttrs().get("href"));
        assertTrue(el.staticAttrs().isEmpty());
    }

    @Test
    void parsesEvent() {
        TemplateNode.Element el = TemplateParser.parse("<button @click=\"save\">x</button>");
        assertEquals("save", el.events().get("click"));
    }

    @Test
    void parsesIfDirective() {
        TemplateNode.Element el = TemplateParser.parse("<p *if=\"count > 0\">x</p>");
        assertEquals("count > 0", el.ifExpr());
    }

    @Test
    void parsesForDirective() {
        TemplateNode.Element el = TemplateParser.parse(
                "<ul><li *for=\"item : items\" key=\"item\">{{ item }}</li></ul>");
        TemplateNode.Element li = (TemplateNode.Element) el.children().get(0);
        assertEquals("item", li.forVar());
        assertEquals("items", li.forExpr());
        assertEquals("item", li.keyExpr());
    }

    @Test
    void parsesNestedElements() {
        TemplateNode.Element el = TemplateParser.parse(
                "<div><span>a</span><span>b</span></div>");
        assertEquals(2, el.children().size());
    }

    @Test
    void parsesSelfClosingAndVoid() {
        TemplateNode.Element el = TemplateParser.parse("<div><br/><img src=\"a.png\"></div>");
        assertEquals(2, el.children().size());
        TemplateNode.Element img = (TemplateNode.Element) el.children().get(1);
        assertEquals("img", img.tag());
        assertTrue(img.children().isEmpty());
    }

    @Test
    void skipsComments() {
        TemplateNode.Element el = TemplateParser.parse("<div><!-- note -->text</div>");
        assertEquals(1, el.children().size());
    }

    @Test
    void dropsInsignificantWhitespaceBetweenElements() {
        // Authored with indentation: the inter-element whitespace text runs are insignificant
        // and must not occupy child positions (so a keyed container stays fully keyed).
        TemplateNode.Element el = TemplateParser.parse(
                "<ul>\n  <li>a</li>\n  <li>b</li>\n</ul>");
        assertEquals(2, el.children().size());
        assertTrue(el.children().get(0) instanceof TemplateNode.Element);
        assertTrue(el.children().get(1) instanceof TemplateNode.Element);
    }

    @Test
    void dropsWhitespaceAroundACommentBetweenElements() {
        // A comment splits one whitespace gap into two adjacent blank text runs. Neither may shield the
        // other from the strip, or a keyed container with an interspersed comment would silently drop to
        // positional diffing (the very thing 6.1 removes).
        TemplateNode.Element el = TemplateParser.parse(
                "<ul><li>a</li>\n  <!-- note -->\n  <li>b</li></ul>");
        assertEquals(2, el.children().size());
        assertTrue(el.children().get(0) instanceof TemplateNode.Element);
        assertTrue(el.children().get(1) instanceof TemplateNode.Element);
    }

    @Test
    void dropsWhitespaceBetweenInlineElements() {
        // Locks the owner-approved rule: inter-element whitespace is insignificant even between inline
        // elements (a blank run needs a NON-blank text or an interpolation neighbour to survive). Use a
        // non-blank run (e.g. an entity) when inline spacing must be preserved.
        TemplateNode.Element el = TemplateParser.parse("<div><span>a</span> <span>b</span></div>");
        assertEquals(2, el.children().size());
        assertTrue(el.children().get(0) instanceof TemplateNode.Element);
        assertTrue(el.children().get(1) instanceof TemplateNode.Element);
    }

    @Test
    void keepsSignificantWhitespaceBetweenInterpolations() {
        // The space between two interpolations is meaningful (word separation) — keep it.
        TemplateNode.Element el = TemplateParser.parse("<span>{{ a }} {{ b }}</span>");
        assertEquals(3, el.children().size());
        TemplateNode mid = el.children().get(1);
        assertTrue(mid instanceof TemplateNode.Text);
        assertEquals(" ", ((TemplateNode.Text) mid).value());
    }

    @Test
    void keepsBlankWhitespaceAdjacentToInterpolation() {
        // A blank run touching an interpolation is meaningful spacing around dynamic text — keep it,
        // even though its other neighbour is an element.
        TemplateNode.Element el = TemplateParser.parse("<div>{{ a }} <span>x</span></div>");
        assertEquals(3, el.children().size());
        assertTrue(el.children().get(0) instanceof TemplateNode.Interpolation);
        assertTrue(el.children().get(1) instanceof TemplateNode.Text);
        assertEquals(" ", ((TemplateNode.Text) el.children().get(1)).value());
        assertTrue(el.children().get(2) instanceof TemplateNode.Element);
    }

    @Test
    void keepsWhitespaceOnlyContentInRawTextElement() {
        // A raw-text element's whitespace is real content — it must survive stripping.
        TemplateNode.Element el = TemplateParser.parse("<textarea>\n</textarea>");
        assertEquals(1, el.children().size());
        assertTrue(el.children().get(0) instanceof TemplateNode.Text);
        assertEquals("\n", ((TemplateNode.Text) el.children().get(0)).value());
    }

    @Test
    void islandElementParses() {
        TemplateNode.Element el = TemplateParser.parse(
                "<div><medley-island name=\"chart\" :data=\"count\"></medley-island></div>");
        TemplateNode.Element island = (TemplateNode.Element) el.children().get(0);
        assertEquals("medley-island", island.tag());
        assertEquals("chart", island.staticAttrs().get("name"));
        assertEquals("count", island.boundAttrs().get("data"));
    }

    @Test
    void mismatchedTagThrows() {
        assertThrows(TemplateException.class, () -> TemplateParser.parse("<div></span>"));
    }

    @Test
    void multipleRootsThrows() {
        assertThrows(TemplateException.class, () -> TemplateParser.parse("<div></div><div></div>"));
    }

    @Test
    void unterminatedInterpolationThrows() {
        assertThrows(TemplateException.class, () -> TemplateParser.parse("<div>{{ count </div>"));
    }
}
