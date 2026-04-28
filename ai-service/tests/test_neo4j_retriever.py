from app.retrieval.neo4j_retriever import Neo4jRetriever
from app.retrieval.neo4j_retriever import format_graph_context


def test_format_graph_context_outputs_entity_edges():
    rows = [
        {"product": "iPhone 15 Pro", "category": "phone", "policy": "7-day return"},
    ]
    context = format_graph_context(rows)
    assert "iPhone 15 Pro" in context
    assert "phone" in context
    assert "7-day return" in context


def test_lookup_product_policy_returns_empty_when_neo4j_query_fails():
    retriever = Neo4jRetriever.__new__(Neo4jRetriever)

    class BrokenSession:
        def __enter__(self):
            raise RuntimeError("neo4j unavailable")

        def __exit__(self, exc_type, exc, tb):
            return False

    class BrokenDriver:
        def session(self):
            return BrokenSession()

    retriever.driver = BrokenDriver()

    assert retriever.lookup_product_policy("iphone") == []
