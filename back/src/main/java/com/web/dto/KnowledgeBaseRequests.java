package com.web.dto;

public class KnowledgeBaseRequests {

    public static class CreateDocumentRequest {
        private String title;
        private String category;
        private String contentText;

        public CreateDocumentRequest() {
        }

        public CreateDocumentRequest(String title, String category, String contentText) {
            this.title = title;
            this.category = category;
            this.contentText = contentText;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getContentText() {
            return contentText;
        }

        public void setContentText(String contentText) {
            this.contentText = contentText;
        }
    }

    public static class UpdateDocumentRequest extends CreateDocumentRequest {
        public UpdateDocumentRequest() {
        }

        public UpdateDocumentRequest(String title, String category, String contentText) {
            super(title, category, contentText);
        }
    }
}
