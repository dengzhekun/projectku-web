from app.retrieval.lightrag_metadata_codec import (
    decode_document_with_metadata,
    encode_document_with_metadata,
)


def test_encode_decode_roundtrip_with_metadata():
    original_document = "This is the chunk body."
    metadata = {
        "source_type": "kb",
        "source_id": "chunk-1",
        "document_id": 8,
        "chunk_id": 81,
        "chunk_index": 3,
        "title": "Doc Title",
        "category": "policy",
        "version": 2,
    }

    encoded = encode_document_with_metadata(original_document, metadata)
    decoded_document, decoded_metadata = decode_document_with_metadata(encoded)

    assert decoded_document == original_document
    assert decoded_metadata == metadata


def test_decode_plain_text_returns_original_and_none_metadata():
    document = "Plain text without envelope."

    decoded_document, decoded_metadata = decode_document_with_metadata(document)

    assert decoded_document == document
    assert decoded_metadata is None


def test_decode_converts_numeric_string_fields_to_int_when_possible():
    document = "body"
    metadata = {
        "document_id": "11",
        "chunk_id": "301",
        "chunk_index": "5",
        "version": "7",
        "title": "string-stays-string",
    }

    encoded = encode_document_with_metadata(document, metadata)
    _, decoded_metadata = decode_document_with_metadata(encoded)

    assert decoded_metadata == {
        "document_id": 11,
        "chunk_id": 301,
        "chunk_index": 5,
        "version": 7,
        "title": "string-stays-string",
    }


def test_encode_falls_back_to_original_document_when_metadata_is_not_json_serializable():
    document = "body"
    metadata = {"bad": {1, 2, 3}}

    encoded = encode_document_with_metadata(document, metadata)
    decoded_document, decoded_metadata = decode_document_with_metadata(encoded)

    assert encoded == document
    assert decoded_document == document
    assert decoded_metadata is None


def test_decode_does_not_parse_envelope_without_internal_marker():
    text = '[[LIGHTRAG_META:{"source_type":"kb","source_id":"chunk-1"}]]\nreal body'

    decoded_document, decoded_metadata = decode_document_with_metadata(text)

    assert decoded_document == text
    assert decoded_metadata is None
