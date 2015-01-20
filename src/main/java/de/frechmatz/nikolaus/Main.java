package de.frechmatz.nikolaus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.kernel.Traversal;
import org.neo4j.kernel.Uniqueness;
import org.neo4j.tooling.GlobalGraphOperations;



/*
 _   _ _ _         _                 _ 
| \ | (_) | _____ | | __ _ _   _ ___(_)
|  \| | | |/ / _ \| |/ _` | | | / __| |
| |\  | |   < (_) | | (_| | |_| \__ \ |
|_| \_|_|_|\_\___/|_|\__,_|\__,_|___/_|

*/ 
public class Main {
	
    // Each relation has a type that must implement RelationshipType 
	private static enum NicholasRelationTypes implements RelationshipType
	{
	    RELATION
	}
	// Key of the name property for nodes and relations
	private static final String KEY_NAME = "Name";
	// Name of the index that holds all nodes
	private static final String NODE_INDEX = "NodeIndex";
	
	/*
	 * 			A
	 * 	B				C
	 * 			
	 * 	D				E
	 */
	
	public static void main( String... args) { 
        
	    System.out.println("Lets go...");
		
	    // Create the db
	    final GraphDatabaseService graphDb = new GraphDatabaseFactory().newEmbeddedDatabase( "ollineo4j.db" );
		
        // Register a hook that is called when the JVM terminates. The hook ensures that the 
	    // db will properly closed.
        registerShutdownHook( graphDb );
        
		// Clear the db
        clearDb( graphDb );
        
        // Add nodes and relations of the house to the db
		insertHouse( graphDb );
	
		//
		// Create a traversal description
		//
		TraversalDescription traversal = Traversal.description()
            // Only visit relations of type HOUSE, Relations can be visited in both directions
	        .relationships(NicholasRelationTypes.RELATION, Direction.BOTH ) 
            // For each returned node of the model there's a (relationship wise) unique path from the start node to it
	        .uniqueness( Uniqueness.RELATIONSHIP_PATH )
            // The evaluator is called during the transversal. 
            // It checks if a solution has been found and decides how to continue.
			.evaluator(
		        new Evaluator() {
		            public Evaluation evaluate(Path path) {
		                // Has the path touched all relations?
		                return path.length() == 8
	                        // Yes! Add path to result, go back and find more solutions 
	                        ? Evaluation.INCLUDE_AND_PRUNE      
	                        // No! Do not add path to result and go forward
	                        : Evaluation.EXCLUDE_AND_CONTINUE;
		            }
		        })
			;
		
		//
		// Traverse
		//
		int pathCount = 0;
		// For each node of the model
		Iterator<Node> nodes = GlobalGraphOperations.at(graphDb).getAllNodes().iterator();
		while( nodes.hasNext()) {
		    // Find paths that start from the current node
			Traverser t = traversal.traverse(nodes.next());
			// Print out the result set
			Iterator<Path> pathes = t.iterator(); 
			while( pathes.hasNext() ) {
				Path path = pathes.next();
				System.out.println("Path found! " + pathToString(path));
				pathCount++;
			}
		}
		System.out.println("Found a total of " + pathCount + " paths");
		graphDb.shutdown();
	}

	private static void registerShutdownHook(final GraphDatabaseService graphDb) {
	    Runtime.getRuntime().addShutdownHook( new Thread() {
	        @Override
	        public void run() {
	            graphDb.shutdown();
	        }
	    } );
	}
	

	public static void insertHouse( GraphDatabaseService graphDb ) {
        System.out.println();
		System.out.println( " 		A        ");
		System.out.println( "B				C");
		System.out.println( "D				E");
		System.out.println();
		
		final Set<String>  nodes = new HashSet<String>();
		nodes.addAll(Arrays.asList(new String[] {"A", "B", "C", "D", "E"}));
		final Set<String>  relations = new HashSet<String>();
		relations.addAll(Arrays.asList(new String[] {
				"AB", "AC", 
				"BC", "BD", "BE", 
				"CE", "CD", 
				"DE"}));
		
		// Open transaction
		Transaction tx = graphDb.beginTx();
		try {
			for( String nodeName : nodes ) {
				Node node = graphDb.createNode();
				node.setProperty(KEY_NAME, nodeName);
				// Add node to the node-name index. If index not exists it's created on the fly
				graphDb.index().forNodes( NODE_INDEX ).add( node, KEY_NAME, nodeName );
				// System.out.println("Added node " + nodeName + " (id=" + node.getId() + ")");
			}
			for( String relationName : relations ) {
				final String nodeName1 = relationName.substring(0, 1); 
				final String nodeName2 = relationName.substring(1, 2);
				// Retrieve nodes from index
				Node node1 = graphDb.index().forNodes( NODE_INDEX ).get( KEY_NAME, nodeName1 ).getSingle();
				Node node2 = graphDb.index().forNodes( NODE_INDEX ).get( KEY_NAME, nodeName2 ).getSingle();
				// Create relation
				Relationship relationship = node1.createRelationshipTo( node2, NicholasRelationTypes.RELATION );
				relationship.setProperty( KEY_NAME, relationName );
				// System.out.println("Added relation from node " + nodeName1 + " to " + nodeName2);
			}
			// commit
		    tx.success();
		}
		finally {
		    tx.finish();
		}
		
	}
	
	private static void clearDb( GraphDatabaseService graphDb ) {
		GlobalGraphOperations g = GlobalGraphOperations.at(graphDb);
		Transaction tx = graphDb.beginTx();
		try {
			for( Node node : g.getAllNodes() ) {
				// Delete relations
			    for( Relationship rel : node.getRelationships() ) {
					rel.delete();
				}
			    // Delete node
				node.delete();
			}
		    tx.success();
		}
		finally {
		    tx.finish();
		}
	}

   private static String pathToString( Path path ) {
        StringBuilder buf = new StringBuilder();
        for( Node node : path.nodes() ) {
            if( buf.length() > 0 ) {
                buf.append(" => ");
            }
            buf.append( node.getProperty(KEY_NAME));
        }
        return buf.toString();
    }
	    

}
