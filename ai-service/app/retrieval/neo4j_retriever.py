from app.config import Settings


def format_graph_context(rows: list[dict]) -> str:
    return "\n".join(
        f"商品:{row['product']} 分类:{row['category']} 政策:{row['policy']}"
        for row in rows
        if row.get("product")
    )


class Neo4jRetriever:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.driver = None
        if settings.neo4j_password:
            from neo4j import GraphDatabase

            self.driver = GraphDatabase.driver(
                settings.neo4j_uri,
                auth=(settings.neo4j_user, settings.neo4j_password),
            )

    def lookup_product_policy(self, keyword: str) -> list[dict]:
        if not self.driver or not keyword.strip():
            return []
        query = """
        MATCH (p:Product)-[:BELONGS_TO]->(c:Category)
        OPTIONAL MATCH (c)-[:APPLIES_POLICY]->(policy:Policy)
        WHERE p.name CONTAINS $keyword
        RETURN p.name AS product, c.name AS category, coalesce(policy.title, '') AS policy
        LIMIT 5
        """
        try:
            with self.driver.session() as session:
                return [record.data() for record in session.run(query, keyword=keyword)]
        except Exception:
            return []
