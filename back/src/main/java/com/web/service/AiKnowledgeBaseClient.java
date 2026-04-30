package com.web.service;

import com.web.pojo.KbChunk;
import com.web.pojo.KbDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

public interface AiKnowledgeBaseClient {
    IndexResult indexDocumentChunks(KbDocument document, List<KbChunk> chunks, boolean recoverMapping);

    void deleteDocument(Long documentId);

    class IndexResult {
        private final String embeddingProvider;
        private final String vectorCollection;
        private final int indexedChunkCount;

        public IndexResult(String embeddingProvider, String vectorCollection, int indexedChunkCount) {
            this.embeddingProvider = embeddingProvider;
            this.vectorCollection = vectorCollection;
            this.indexedChunkCount = indexedChunkCount;
        }

        public String getEmbeddingProvider() {
            return embeddingProvider;
        }

        public String getVectorCollection() {
            return vectorCollection;
        }

        public int getIndexedChunkCount() {
            return indexedChunkCount;
        }
    }
}

@Component
@ConditionalOnMissingBean(AiKnowledgeBaseClient.class)
class NoopAiKnowledgeBaseClient implements AiKnowledgeBaseClient {
    @Override
    public IndexResult indexDocumentChunks(KbDocument document, List<KbChunk> chunks, boolean recoverMapping) {
        return new IndexResult("noop", "noop", chunks == null ? 0 : chunks.size());
    }

    @Override
    public void deleteDocument(Long documentId) {
        // no-op
    }
}
