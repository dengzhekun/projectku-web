package com.web.mapper;

import com.web.pojo.KbChunk;
import com.web.pojo.CustomerServiceLog;
import com.web.pojo.KbDocument;
import com.web.pojo.KbHitLog;
import com.web.pojo.KbIndexRecord;
import com.web.pojo.KbMissLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {
    int insertDocument(KbDocument document);

    int updateDocument(KbDocument document);

    KbDocument getDocumentById(Long id);

    List<KbDocument> getDocuments(@Param("category") String category, @Param("status") String status, @Param("keyword") String keyword);

    int deleteDocument(Long id);

    int deleteChunksByDocumentId(Long documentId);

    int insertChunk(KbChunk chunk);

    List<KbChunk> getChunksByDocumentId(Long documentId);

    int deleteIndexRecordsByDocumentId(Long documentId);

    int insertIndexRecord(KbIndexRecord record);

    List<KbIndexRecord> getIndexRecordsByDocumentId(Long documentId);

    int deleteHitLogsByDocumentId(Long documentId);

    int insertHitLog(KbHitLog hitLog);

    List<KbHitLog> getHitLogsByDocumentId(Long documentId);

    int insertMissLog(KbMissLog missLog);

    List<KbMissLog> getMissLogs(@Param("status") String status, @Param("keyword") String keyword);

    int insertCustomerServiceLog(CustomerServiceLog log);

    List<CustomerServiceLog> getCustomerServiceLogs(
            @Param("route") String route,
            @Param("sourceType") String sourceType,
            @Param("keyword") String keyword);
}
