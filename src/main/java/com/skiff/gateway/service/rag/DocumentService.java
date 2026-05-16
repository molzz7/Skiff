package com.skiff.gateway.service.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import com.skiff.gateway.model.dto.DocumentInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文档服务 — 上传、解析、分块、向量化、存储 (PgVector 持久化)
 */
@Slf4j
@Service
public class DocumentService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DataSource dataSource;

    private static final int MAX_CHUNK_SIZE = 500;
    private static final int MAX_OVERLAP = 50;

    public DocumentService(EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore,
                           DataSource dataSource) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.dataSource = dataSource;
    }

    /** 上传并处理文档，返回文档ID */
    public String processDocument(Path tempFile, String fileName) {
        String docId = UUID.randomUUID().toString().substring(0, 12);
        String text = extractText(tempFile, fileName);
        log.info("Parsed document: {} ({} chars)", fileName, text.length());

        Document doc = Document.from(text, Metadata.from("file_name", fileName));
        DocumentSplitter splitter = DocumentSplitters.recursive(MAX_CHUNK_SIZE, MAX_OVERLAP);
        List<TextSegment> segments = splitter.split(doc);

        // 给每个分块附加文档元数据
        String uploadedAt = LocalDateTime.now().toString();
        for (TextSegment seg : segments) {
            seg.metadata().put("document_id", docId);
            seg.metadata().put("file_name", fileName);
            seg.metadata().put("uploaded_at", uploadedAt);
        }

        log.info("Split into {} chunks (max {} chars, overlap {})", segments.size(), MAX_CHUNK_SIZE, MAX_OVERLAP);

        Response<List<Embedding>> response = embeddingModel.embedAll(segments);
        embeddingStore.addAll(response.content(), segments);

        log.info("Embedded and stored {} chunks for docId={} file={}", segments.size(), docId, fileName);
        return docId;
    }

    /** 删除指定文档的所有分块 */
    public void deleteDocument(String documentId) {
        embeddingStore.removeAll(
                MetadataFilterBuilder.metadataKey("document_id").isEqualTo(documentId));
        log.info("Deleted document: {}", documentId);
    }

    /** 清空全部知识库 */
    public void clearStore() {
        embeddingStore.removeAll();
        log.info("Embedding store cleared");
    }

    /** 查询已上传文档列表 */
    public List<DocumentInfo> listDocuments() {
        List<DocumentInfo> docs = new ArrayList<>();
        String sql = """
                SELECT DISTINCT
                    metadata->>'document_id' AS doc_id,
                    metadata->>'file_name' AS file_name,
                    metadata->>'uploaded_at' AS uploaded_at,
                    COUNT(*) AS chunks
                FROM skiff_embeddings
                WHERE metadata->>'document_id' IS NOT NULL
                GROUP BY metadata->>'document_id', metadata->>'file_name', metadata->>'uploaded_at'
                ORDER BY metadata->>'uploaded_at' DESC
                """;
        try (Connection conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                docs.add(DocumentInfo.builder()
                        .id(rs.getString("doc_id"))
                        .fileName(rs.getString("file_name"))
                        .chunks(rs.getInt("chunks"))
                        .uploadedAt(rs.getString("uploaded_at"))
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to query document list", e);
        }
        return docs;
    }

    private String extractText(Path file, String fileName) {
        String ext = fileName.toLowerCase();
        try {
            if (ext.endsWith(".pdf")) {
                try (var pdf = Loader.loadPDF(file.toFile())) {
                    return new PDFTextStripper().getText(pdf);
                }
            } else if (ext.endsWith(".docx")) {
                try (var is = Files.newInputStream(file);
                     var doc = new XWPFDocument(is);
                     var extractor = new XWPFWordExtractor(doc)) {
                    return extractor.getText();
                }
            } else if (ext.endsWith(".txt") || ext.endsWith(".md")) {
                return Files.readString(file);
            }
        } catch (Exception e) {
            throw new RuntimeException("无法解析文件 " + fileName + ": " + e.getMessage(), e);
        }
        throw new IllegalArgumentException("不支持的文件格式: " + ext + "（支持 pdf/docx/txt/md）");
    }
}
