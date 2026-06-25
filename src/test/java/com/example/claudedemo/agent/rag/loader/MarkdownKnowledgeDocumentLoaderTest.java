package com.example.claudedemo.agent.rag.loader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MarkdownKnowledgeDocumentLoader} 内置解析方法的单元测试.
 *
 * @since 0.0.1
 */
class MarkdownKnowledgeDocumentLoaderTest {

    // ==================== extractTitle ====================

    @Test
    void should_extract_title_from_h1_line() {
        assertEquals("users 表", MarkdownKnowledgeDocumentLoader.extractTitle("# users 表\ncontent", "fallback"));
        assertEquals("test", MarkdownKnowledgeDocumentLoader.extractTitle("# test", "f"));
        assertEquals("a b c", MarkdownKnowledgeDocumentLoader.extractTitle("# a b c\n\nbody", "f"));
    }

    @Test
    void should_fallback_when_no_h1() {
        assertEquals("fallback", MarkdownKnowledgeDocumentLoader.extractTitle("content only\nno h1", "fallback"));
        assertEquals("f", MarkdownKnowledgeDocumentLoader.extractTitle("", "f"));
        assertEquals("f", MarkdownKnowledgeDocumentLoader.extractTitle(null, "f"));
        assertEquals("f", MarkdownKnowledgeDocumentLoader.extractTitle("   ", "f"));
    }

    @Test
    void should_fallback_when_second_line_is_h1() {
        assertEquals("f", MarkdownKnowledgeDocumentLoader.extractTitle("first line\n# title", "f"));
    }

    // ==================== parseFile ====================

    @Test
    void should_parse_md_filename() {
        var doc = MarkdownKnowledgeDocumentLoader.parseFile("users.md", "# users 表\ncontent", "s");
        assertEquals("users", doc.id());
        assertEquals("users 表", doc.title());
        assertEquals("# users 表\ncontent", doc.content());
    }

    @Test
    void should_parse_filename_without_md() {
        var doc = MarkdownKnowledgeDocumentLoader.parseFile("my-doc", "content", "s");
        assertEquals("my-doc", doc.id());
    }

    @Test
    void should_include_metadata_path() {
        var doc = MarkdownKnowledgeDocumentLoader.parseFile("a.md", "# A\nbody", "src/a.md");
        assertEquals("src/a.md", doc.metadataView().get("path"));
    }
}
