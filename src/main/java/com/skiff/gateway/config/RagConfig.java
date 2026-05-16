package com.skiff.gateway.config;

import com.skiff.gateway.service.settings.SettingsService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * RAG 配置 — PgVector 持久化向量存储
 */
@Slf4j
@Configuration
public class RagConfig {

    private final SettingsService settingsService;

    public RagConfig(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** PgVector 数据源 */
    @Bean
    public DataSource pgDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:postgresql://localhost:5432/skiff")
                .username("skiff")
                .password("skiff123")
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    /** 向量化模型 */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(settingsService.getBaseUrl())
                .apiKey(settingsService.getApiKey())
                .modelName("text-embedding-3-small")
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    /** PgVector 向量存储 (维度 1536 for text-embedding-3-small) */
    @Bean
    public EmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore(DataSource pgDataSource) {
        log.info("EmbeddingStore: PgVector (table=skiff_embeddings, dim=1536)");
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(pgDataSource)
                .table("skiff_embeddings")
                .dimension(1536)
                .createTable(true)
                .dropTableFirst(false)
                .build();
    }

    /** 内容检索器 */
    @Bean
    public ContentRetriever contentRetriever(
            EmbeddingStore<dev.langchain4j.data.segment.TextSegment> store,
            EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(5)
                .minScore(0.4)
                .build();
    }
}
