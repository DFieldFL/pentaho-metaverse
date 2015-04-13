/*!
 * PENTAHO CORPORATION PROPRIETARY AND CONFIDENTIAL
 *
 * Copyright 2002 - 2015 Pentaho Corporation (Pentaho). All rights reserved.
 *
 * NOTICE: All information including source code contained herein is, and
 * remains the sole property of Pentaho and its licensors. The intellectual
 * and technical concepts contained herein are proprietary and confidential
 * to, and are trade secrets of Pentaho and may be covered by U.S. and foreign
 * patents, or patents in process, and are protected by trade secret and
 * copyright laws. The receipt or possession of this source code and/or related
 * information does not convey or imply any rights to reproduce, disclose or
 * distribute its contents, or to manufacture, use, or sell anything that it
 * may describe, in whole or in part. Any reproduction, modification, distribution,
 * or public display of this information without the express written authorization
 * from Pentaho is strictly prohibited and in violation of applicable laws and
 * international treaties. Access to the source code contained herein is strictly
 * prohibited to anyone except those individuals and entities who have executed
 * confidentiality and non-disclosure agreements or other agreements with Pentaho,
 * explicitly covering such access.
 */

package com.pentaho.metaverse.util;

import com.pentaho.dictionary.DictionaryConst;
import com.pentaho.dictionary.DictionaryHelper;
import com.pentaho.metaverse.analyzer.kettle.ChangeType;
import com.pentaho.metaverse.api.IDocumentController;
import com.pentaho.metaverse.api.model.IOperation;
import com.pentaho.metaverse.api.model.Operation;
import com.pentaho.metaverse.api.model.Operations;
import com.pentaho.metaverse.graph.LineageGraphCompletionService;
import com.pentaho.metaverse.graph.LineageGraphMap;
import com.pentaho.metaverse.impl.MetaverseBuilder;
import com.pentaho.metaverse.api.MetaverseComponentDescriptor;
import com.pentaho.metaverse.messages.Messages;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import flexjson.JSONDeserializer;
import org.pentaho.di.core.Const;
import com.pentaho.metaverse.api.IDocument;
import com.pentaho.metaverse.api.IDocumentAnalyzer;
import com.pentaho.metaverse.api.IMetaverseBuilder;
import com.pentaho.metaverse.api.INamespace;
import com.pentaho.metaverse.api.MetaverseAnalyzerException;
import com.pentaho.metaverse.api.MetaverseException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;


/**
 * A class containing utility methods for Metaverse / lineage
 */
public class MetaverseUtil {

  public static final String MESSAGE_PREFIX_NODETYPE = "USER.nodetype.";
  public static final String MESSAGE_PREFIX_LINKTYPE = "USER.linktype.";
  public static final String MESSAGE_PREFIX_CATEGORY = "USER.category.";
  public static final String MESSAGE_FAILED_PREFIX = "!";

  protected static IDocumentController documentController = null;

  public static IDocumentController getDocumentController() {
    if ( documentController != null ) {
      return documentController;
    }
    try {
      documentController = (IDocumentController) MetaverseBeanUtil.getInstance().get( "IDocumentController" );
    } catch ( Exception e ) {
      // Just return null
    }
    return documentController;
  }

  public static void setDocumentController( IDocumentController docController ) {
    documentController = docController;
  }

  public static IDocument createDocument(
    INamespace namespace,
    Object content,
    String id,
    String name,
    String extension,
    String mimeType ) {

    IDocument metaverseDocument = getDocumentController().getMetaverseObjectFactory().createDocumentObject();

    metaverseDocument.setNamespace( namespace );
    metaverseDocument.setContent( content );
    metaverseDocument.setStringID( id );
    metaverseDocument.setName( name );
    metaverseDocument.setExtension( extension );
    metaverseDocument.setMimeType( mimeType );
    metaverseDocument.setProperty( DictionaryConst.PROPERTY_PATH, id );
    metaverseDocument.setProperty( DictionaryConst.PROPERTY_NAMESPACE, namespace.getNamespaceId() );

    return metaverseDocument;
  }

  public static void addLineageGraph( final IDocument document, Graph graph ) throws MetaverseException {

    if ( document == null ) {
      throw new MetaverseException( Messages.getString( "ERROR.Document.IsNull" ) );
    }

    // Find the transformation analyzer(s) and create Futures to analyze the transformation.
    // Right now we expect a single transformation analyzer in the system. If we need to support more,
    // the lineageGraphMap needs to map the TransMeta to a collection of Futures, etc.
    IDocumentController docController = MetaverseUtil.getDocumentController();
    if ( docController != null ) {

      // Create a new builder, setting it on the DocumentController if possible
      IMetaverseBuilder metaverseBuilder = new MetaverseBuilder( graph );

      docController.setMetaverseBuilder( metaverseBuilder );
      List<IDocumentAnalyzer> matchingAnalyzers = docController.getDocumentAnalyzers( "ktr" );

      if ( matchingAnalyzers != null ) {
        for ( final IDocumentAnalyzer analyzer : matchingAnalyzers ) {

          Runnable analyzerRunner = getAnalyzerRunner( analyzer, document );

          Graph g = ( graph != null ) ? graph : new TinkerGraph();
          Future<Graph> transAnalysis =
            LineageGraphCompletionService.getInstance().submit( analyzerRunner, g );

          // Save this Future, the client will call it when the analysis is needed
          LineageGraphMap.getInstance().put( document.getContent(), transAnalysis );
        }
      }
    }
  }

  /**
   * Enhances an edge by adding a localized type
   *
   * @param edge The edge to enhance
   */
  public static void enhanceEdge( Edge edge ) {
    String type = edge.getLabel();
    //localize the node type
    String localizedType = Messages.getString( MESSAGE_PREFIX_LINKTYPE + type );
    if ( !localizedType.startsWith( MESSAGE_FAILED_PREFIX ) ) {
      edge.setProperty( DictionaryConst.PROPERTY_TYPE_LOCALIZED, localizedType );
    }
  }

  /**
   * Enhances a vertex by adding localized type and category, and a suggested color
   *
   * @param vertex The vertex to enhance
   */
  public static void enhanceVertex( Vertex vertex ) {
    String type = vertex.getProperty( DictionaryConst.PROPERTY_TYPE );
    //localize the node type
    String localizedType = Messages.getString( MESSAGE_PREFIX_NODETYPE + type );
    if ( !localizedType.startsWith( MESSAGE_FAILED_PREFIX ) ) {
      vertex.setProperty( DictionaryConst.PROPERTY_TYPE_LOCALIZED, localizedType );
    }
    // get the vertex category and set it
    String category = DictionaryHelper.getCategoryForType( type );
    vertex.setProperty( DictionaryConst.PROPERTY_CATEGORY, category );
    // get the vertex category color and set it
    String color = DictionaryHelper.getColorForCategory( category );
    vertex.setProperty( DictionaryConst.PROPERTY_COLOR, color );
    //localize the category
    String localizedCat = Messages.getString( MESSAGE_PREFIX_CATEGORY + category );
    if ( !localizedCat.startsWith( MESSAGE_FAILED_PREFIX ) ) {
      vertex.setProperty( DictionaryConst.PROPERTY_CATEGORY_LOCALIZED, localizedCat );
    }
  }

  public static Operations convertOperationsStringToMap( String operations ) {
    Operations resultOps = null;
    if ( !Const.isEmpty( operations ) ) {
      try {
        Map<String, List<IOperation>> rawOpsMap = new JSONDeserializer<Map<String, List<IOperation>>>().
          use( "values.values", Operation.class ).
          deserialize( operations );
        resultOps = new Operations();
        for ( String key : rawOpsMap.keySet() ) {
          resultOps.put( ChangeType.forValue( key ), rawOpsMap.get( key ) );
        }
      } catch ( Exception e ) {
        resultOps = null;
      }
      //return new JSONDeserializer<Operations>().use(null, Operations.class).deserialize( operations );
    }
    return resultOps;
  }

  public static Runnable getAnalyzerRunner( final IDocumentAnalyzer analyzer, final IDocument document ) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          MetaverseComponentDescriptor docDescriptor = new MetaverseComponentDescriptor(
            document.getName(),
            DictionaryConst.NODE_TYPE_TRANS,
            document.getNamespace() );
          if ( document.getContext() != null ) {
            docDescriptor.setContext( document.getContext() );
          }
          analyzer.analyze( docDescriptor, document );
        } catch ( MetaverseAnalyzerException mae ) {
          throw new RuntimeException( Messages.getString( "ERROR.AnalyzingDocument", document.getNamespaceId() ), mae );
        }
      }
    };
  }
}