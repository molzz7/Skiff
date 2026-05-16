package com.skiff.gateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfo {

    /** 文档唯一ID */
    private String id;

    /** 原始文件名 */
    private String fileName;

    /** 分块数量 */
    private int chunks;

    /** 上传时间 */
    private String uploadedAt;
}
