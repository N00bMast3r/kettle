 /* Copyright (c) 2007 Pentaho Corporation.  All rights reserved. 
 * This software was developed by Pentaho Corporation and is provided under the terms 
 * of the GNU Lesser General Public License, Version 2.1. You may not use 
 * this file except in compliance with the license. If you need a copy of the license, 
 * please go to http://www.gnu.org/licenses/lgpl-2.1.txt. The Original Code is Pentaho 
 * Data Integration.  The Initial Developer is Pentaho Corporation.
 *
 * Software distributed under the GNU Lesser Public License is distributed on an "AS IS" 
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or  implied. Please refer to 
 * the license for the specific language governing your rights and limitations.*/

package org.pentaho.di.trans.steps.mysqlbulkloader;

import java.util.List;
import java.util.Map;

import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Counter;
import org.pentaho.di.core.SQLStatement;
import org.pentaho.di.core.database.Database;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.shared.SharedObjectInterface;
import org.pentaho.di.trans.DatabaseImpact;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.w3c.dom.Node;


/**
 * Here are the steps that we need to take to make streaming loading possible for MySQL:<br>
 * <br>
 * The following steps are carried out by the step at runtime:<br>
 * <br>
 * - create a unique FIFO file (using mkfifo, LINUX ONLY FOLKS!)<br>
 * - Create a target table using standard Kettle SQL generation<br>
 * - Execute the LOAD DATA SQL Command to bulk load in a separate SQL thread in the background:<br>
 * - Write to the FIFO file<br>
 * - At the end, close the output stream to the FIFO file<br>
 * * At the end, remove the FIFO file
 * <br>
		

 * Created on 24-oct-2007<br>
 * @author Matt Casters<br>
 */
public class MySQLBulkLoaderMeta extends BaseStepMeta implements StepMetaInterface
{
	public static final int FIELD_FORMAT_TYPE_OK = 0;
	public static final int FIELD_FORMAT_TYPE_DATE = 1;
	public static final int FIELD_FORMAT_TYPE_TIMESTAMP = 2;
	public static final int FIELD_FORMAT_TYPE_NUMBER = 3;
	public static final int FIELD_FORMAT_TYPE_STRING_ESCAPE = 4;
	
	private static final String[] fieldFormatTypeCodes = { "OK", "DATE", "TIMESTAMP", "NUMBER", "STRING_ESC" };
	private static final String[] fieldFormatTypeDescriptions = { 
		Messages.getString("MySQLBulkLoaderMeta.FieldFormatType.OK.Description"), 
		Messages.getString("MySQLBulkLoaderMeta.FieldFormatType.Date.Description"), 
		Messages.getString("MySQLBulkLoaderMeta.FieldFormatType.Timestamp.Description"), 
		Messages.getString("MySQLBulkLoaderMeta.FieldFormatType.Number.Description"),
		Messages.getString("MySQLBulkLoaderMeta.FieldFormatType.StringEscape.Description"),
	};
	
    /** what's the schema for the target? */
    private String schemaName;

    /** what's the table for the target? */
	private String tableName;

	/** The name of the FIFO file to create */
	private String fifoFileName;
	
    /** database connection */
	private DatabaseMeta databaseMeta;

    /** Field name of the target table */
	private String fieldTable[];

    /** Field name in the stream */
	private String fieldStream[];

	/** flag to indicate what to do with the formatting */
	private int fieldFormatType[];

	/** Encoding to use */
	private String encoding;
	
	/** REPLACE clause flag */
	private boolean replacingData;
	
	/** IGNORE clause flag */
	private boolean ignoringErrors;
	
	/** The delimiter to use */
	private String delimiter;
	
	/** The enclosure to use */
	private String enclosure;
	
	/** The escape character */
	private String escapeChar;	
		
	public MySQLBulkLoaderMeta()
	{
		super();
	}

    /**
     * @return Returns the database.
     */
    public DatabaseMeta getDatabaseMeta()
    {
        return databaseMeta;
    }

    /**
     * @param database The database to set.
     */
    public void setDatabaseMeta(DatabaseMeta database)
    {
        this.databaseMeta = database;
    }

    /**
     * @return Returns the tableName.
     */
    public String getTableName()
    {
        return tableName;
    }

    /**
     * @param tableName The tableName to set.
     */
    public void setTableName(String tableName)
    {
        this.tableName = tableName;
    }
    
    /**
     * @return Returns the fieldTable.
     */
    public String[] getFieldTable()
    {
        return fieldTable;
    }

    /**
     * @param fieldTable The fieldTable to set.
     */
    public void setFieldTable(String[] fieldTable)
    {
        this.fieldTable = fieldTable;
    }

    /**
     * @return Returns the fieldStream.
     */
    public String[] getFieldStream()
    {
        return fieldStream;
    }

    /**
     * @param fieldStream The fieldStream to set.
     */
    public void setFieldStream(String[] fieldStream)
    {
        this.fieldStream = fieldStream;
    }

	public void loadXML(Node stepnode, List<DatabaseMeta> databases, Map<String, Counter> counters)
		throws KettleXMLException
	{
		readData(stepnode, databases);
	}

	public void allocate(int nrvalues)
	{
		fieldTable  = new String[nrvalues];
		fieldStream = new String[nrvalues];
		fieldFormatType = new int[nrvalues];
	}

	public Object clone()
	{
		MySQLBulkLoaderMeta retval = (MySQLBulkLoaderMeta)super.clone();
		int nrvalues  = fieldTable.length;

		retval.allocate(nrvalues);

		for (int i=0;i<nrvalues;i++)
		{
			retval.fieldTable[i]  = fieldTable[i];
			retval.fieldStream[i] = fieldStream[i];
		}
		return retval;
	}

	private void readData(Node stepnode, List<? extends SharedObjectInterface> databases)
		throws KettleXMLException
	{
		try
		{
			String con     = XMLHandler.getTagValue(stepnode, "connection");   //$NON-NLS-1$
			databaseMeta   = DatabaseMeta.findDatabase(databases, con);
            
            schemaName     = XMLHandler.getTagValue(stepnode, "schema");       //$NON-NLS-1$
			tableName      = XMLHandler.getTagValue(stepnode, "table");        //$NON-NLS-1$

 			fifoFileName   = XMLHandler.getTagValue(stepnode, "fifo_file_name");        //$NON-NLS-1$

			encoding       = XMLHandler.getTagValue(stepnode, "encoding");         //$NON-NLS-1$
			enclosure      = XMLHandler.getTagValue(stepnode, "enclosure");         //$NON-NLS-1$
			delimiter      = XMLHandler.getTagValue(stepnode, "delimiter");         //$NON-NLS-1$
			escapeChar     = XMLHandler.getTagValue(stepnode, "escape_char");         //$NON-NLS-1$
			
			replacingData  = "Y".equalsIgnoreCase(XMLHandler.getTagValue(stepnode, "replace"));         //$NON-NLS-1$
			ignoringErrors = "Y".equalsIgnoreCase(XMLHandler.getTagValue(stepnode, "ignore"));         //$NON-NLS-1$

			int nrvalues = XMLHandler.countNodes(stepnode, "mapping");      //$NON-NLS-1$
			allocate(nrvalues);

			for (int i=0;i<nrvalues;i++)
			{
				Node vnode = XMLHandler.getSubNodeByNr(stepnode, "mapping", i);    //$NON-NLS-1$

				fieldTable[i]      = XMLHandler.getTagValue(vnode, "stream_name"); //$NON-NLS-1$
				fieldStream[i]     = XMLHandler.getTagValue(vnode, "field_name");  //$NON-NLS-1$
				if (fieldStream[i]==null) fieldStream[i]=fieldTable[i];            // default: the same name!
				fieldFormatType[i]  = getFieldFormatType(XMLHandler.getTagValue(vnode, "field_format_type"));  //$NON-NLS-1$
			}
		}
		catch(Exception e)
		{
			throw new KettleXMLException(Messages.getString("MySQLBulkLoaderMeta.Exception.UnableToReadStepInfoFromXML"), e); //$NON-NLS-1$
		}
	}

	public void setDefault()
	{
		fieldTable   = null;
		databaseMeta = null;
        schemaName   = "";                //$NON-NLS-1$
		tableName    = Messages.getString("MySQLBulkLoaderMeta.DefaultTableName"); //$NON-NLS-1$
        encoding     = "";                                       //$NON-NLS-1$
        fifoFileName = "/tmp/fifo";
        delimiter    = "\t";
        enclosure    = "\"";
        escapeChar   = "\\";
        replacingData = false;
        ignoringErrors = false;
        
		allocate(0);
	}

	public String getXML()
	{
        StringBuffer retval = new StringBuffer(300);

		retval.append("    ").append(XMLHandler.addTagValue("connection",   databaseMeta==null?"":databaseMeta.getName())); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        retval.append("    ").append(XMLHandler.addTagValue("schema",       schemaName));    //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("table",        tableName));     //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("encoding",     encoding));      //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("delimiter",    delimiter));      //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("enclosure",    enclosure));      //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("escape_char",  escapeChar));      //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("replace",      replacingData));      //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("    ").append(XMLHandler.addTagValue("ignore",       ignoringErrors));      //$NON-NLS-1$ //$NON-NLS-2$

		retval.append("    ").append(XMLHandler.addTagValue("fifo_file_name", fifoFileName));      //$NON-NLS-1$ //$NON-NLS-2$

		for (int i=0;i<fieldTable.length;i++)
		{
			retval.append("      <mapping>").append(Const.CR); //$NON-NLS-1$
			retval.append("        ").append(XMLHandler.addTagValue("stream_name", fieldTable[i])); //$NON-NLS-1$ //$NON-NLS-2$
			retval.append("        ").append(XMLHandler.addTagValue("field_name",  fieldStream[i])); //$NON-NLS-1$ //$NON-NLS-2$
			retval.append("        ").append(XMLHandler.addTagValue("field_format_ok",  getFieldFormatTypeCode(fieldFormatType[i]))); //$NON-NLS-1$ //$NON-NLS-2$
			retval.append("      </mapping>").append(Const.CR); //$NON-NLS-1$
		}

		return retval.toString();
	}

	public void readRep(Repository rep, long id_step, List<DatabaseMeta> databases, Map<String, Counter> counters)
		throws KettleException
	{
		try
		{
			long id_connection =  rep.getStepAttributeInteger(id_step, "id_connection");  //$NON-NLS-1$
			databaseMeta   =      DatabaseMeta.findDatabase( databases, id_connection);
            schemaName     =      rep.getStepAttributeString(id_step,  "schema");         //$NON-NLS-1$
			tableName      =      rep.getStepAttributeString(id_step,  "table");          //$NON-NLS-1$
			encoding       =      rep.getStepAttributeString(id_step,  "encoding");       //$NON-NLS-1$
			enclosure      =      rep.getStepAttributeString(id_step,  "enclosure");       //$NON-NLS-1$
			delimiter      =      rep.getStepAttributeString(id_step,  "delimiter");       //$NON-NLS-1$
			escapeChar     =      rep.getStepAttributeString(id_step,  "escape_char");       //$NON-NLS-1$
			fifoFileName   =      rep.getStepAttributeString(id_step,  "fifo_file_name");       //$NON-NLS-1$
			replacingData  =      rep.getStepAttributeBoolean(id_step, "replace");       //$NON-NLS-1$
			ignoringErrors =      rep.getStepAttributeBoolean(id_step, "ignore");       //$NON-NLS-1$
			
			int nrvalues = rep.countNrStepAttributes(id_step, "stream_name");             //$NON-NLS-1$

			allocate(nrvalues);

			for (int i=0;i<nrvalues;i++)
			{
				fieldTable[i]  = rep.getStepAttributeString(id_step, i, "stream_name");   //$NON-NLS-1$
				fieldStream[i] = rep.getStepAttributeString(id_step, i, "field_name");    //$NON-NLS-1$
				if (fieldStream[i]==null) fieldStream[i]=fieldTable[i];        
				fieldFormatType[i] = getFieldFormatType(rep.getStepAttributeString(id_step, i, "field_format_type"));    //$NON-NLS-1$
			}
		}
		catch(Exception e)
		{
			throw new KettleException(Messages.getString("MySQLBulkLoaderMeta.Exception.UnexpectedErrorReadingStepInfoFromRepository"), e); //$NON-NLS-1$
		}
	}

	public void saveRep(Repository rep, long id_transformation, long id_step)
		throws KettleException
	{
		try
		{
			rep.saveStepAttribute(id_transformation, id_step, "id_connection",    databaseMeta==null?-1:databaseMeta.getID()); //$NON-NLS-1$
            rep.saveStepAttribute(id_transformation, id_step, "schema",           schemaName);    //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "table",            tableName);     //$NON-NLS-1$
			
			rep.saveStepAttribute(id_transformation, id_step, "encoding",         encoding);      //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "enclosure",        enclosure);      //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "delimiter",        delimiter);      //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "escape_char",      escapeChar);      //$NON-NLS-1$

			rep.saveStepAttribute(id_transformation, id_step, "fifo_file_name", fifoFileName);      //$NON-NLS-1$

			rep.saveStepAttribute(id_transformation, id_step, "replace", replacingData);      //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "ignore", ignoringErrors);      //$NON-NLS-1$

			for (int i=0;i<fieldTable.length;i++)
			{
				rep.saveStepAttribute(id_transformation, id_step, i, "stream_name", fieldTable[i]);  //$NON-NLS-1$
				rep.saveStepAttribute(id_transformation, id_step, i, "field_name",  fieldStream[i]); //$NON-NLS-1$
				rep.saveStepAttribute(id_transformation, id_step, i, "field_format_ok",  getFieldFormatTypeCode(fieldFormatType[i])); //$NON-NLS-1$
			}

			// Also, save the step-database relationship!
			if (databaseMeta!=null) rep.insertStepDatabase(id_transformation, id_step, databaseMeta.getID());
		}
		catch(Exception e)
		{
			throw new KettleException(Messages.getString("MySQLBulkLoaderMeta.Exception.UnableToSaveStepInfoToRepository")+id_step, e); //$NON-NLS-1$
		}
	}
	
	public void getFields(RowMetaInterface rowMeta, String origin, RowMetaInterface[] info, StepMeta nextStep, VariableSpace space) throws KettleStepException
	{
		// Default: nothing changes to rowMeta
	}

	public void check(List<CheckResultInterface> remarks, TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev, String input[], String output[], RowMetaInterface info)
	{
		CheckResult cr;
		String error_message = ""; //$NON-NLS-1$

		if (databaseMeta!=null)
		{
			Database db = new Database(databaseMeta);
			db.shareVariablesWith(transMeta);
			try
			{
				db.connect();

				if (!Const.isEmpty(tableName))
				{
					cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, Messages.getString("MySQLBulkLoaderMeta.CheckResult.TableNameOK"), stepMeta); //$NON-NLS-1$
					remarks.add(cr);

					boolean first=true;
					boolean error_found=false;
					error_message = ""; //$NON-NLS-1$
					
					// Check fields in table
                    String schemaTable = databaseMeta.getQuotedSchemaTableCombination(
                    		                   transMeta.environmentSubstitute(schemaName), 
                    		                   transMeta.environmentSubstitute(tableName));
					RowMetaInterface r = db.getTableFields(schemaTable);
					if (r!=null)
					{
						cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, Messages.getString("MySQLBulkLoaderMeta.CheckResult.TableExists"), stepMeta); //$NON-NLS-1$
						remarks.add(cr);

						// How about the fields to insert/dateMask in the table?
						first=true;
						error_found=false;
						error_message = ""; //$NON-NLS-1$
						
						for (int i=0;i<fieldTable.length;i++)
						{
							String field = fieldTable[i];

							ValueMetaInterface v = r.searchValueMeta(field);
							if (v==null)
							{
								if (first)
								{
									first=false;
									error_message+=Messages.getString("MySQLBulkLoaderMeta.CheckResult.MissingFieldsToLoadInTargetTable")+Const.CR; //$NON-NLS-1$
								}
								error_found=true;
								error_message+="\t\t"+field+Const.CR;  //$NON-NLS-1$
							}
						}
						if (error_found)
						{
							cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
						}
						else
						{
							cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, Messages.getString("MySQLBulkLoaderMeta.CheckResult.AllFieldsFoundInTargetTable"), stepMeta); //$NON-NLS-1$
						}
						remarks.add(cr);
					}
					else
					{
						error_message=Messages.getString("MySQLBulkLoaderMeta.CheckResult.CouldNotReadTableInfo"); //$NON-NLS-1$
						cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
						remarks.add(cr);
					}
				}

				// Look up fields in the input stream <prev>
				if (prev!=null && prev.size()>0)
				{
					cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, Messages.getString("MySQLBulkLoaderMeta.CheckResult.StepReceivingDatas",prev.size()+""), stepMeta); //$NON-NLS-1$ //$NON-NLS-2$
					remarks.add(cr);

					boolean first=true;
					error_message = ""; //$NON-NLS-1$
					boolean error_found = false;

					for (int i=0;i<fieldStream.length;i++)
					{
						ValueMetaInterface v = prev.searchValueMeta(fieldStream[i]);
						if (v==null)
						{
							if (first)
							{
								first=false;
								error_message+=Messages.getString("MySQLBulkLoaderMeta.CheckResult.MissingFieldsInInput")+Const.CR; //$NON-NLS-1$
							}
							error_found=true;
							error_message+="\t\t"+fieldStream[i]+Const.CR;  //$NON-NLS-1$
						}
					}
					if (error_found)
 					{
						cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
					}
					else
					{
						cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, Messages.getString("MySQLBulkLoaderMeta.CheckResult.AllFieldsFoundInInput"), stepMeta); //$NON-NLS-1$
					}
					remarks.add(cr);
				}
				else
				{
					error_message=Messages.getString("MySQLBulkLoaderMeta.CheckResult.MissingFieldsInInput3")+Const.CR; //$NON-NLS-1$
					cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
					remarks.add(cr);
				}
			}
			catch(KettleException e)
			{
				error_message = Messages.getString("MySQLBulkLoaderMeta.CheckResult.DatabaseErrorOccurred")+e.getMessage(); //$NON-NLS-1$
				cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
				remarks.add(cr);
			}
			finally
			{
				db.disconnect();
			}
		}
		else
		{
			error_message = Messages.getString("MySQLBulkLoaderMeta.CheckResult.InvalidConnection"); //$NON-NLS-1$
			cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, error_message, stepMeta);
			remarks.add(cr);
		}

		// See if we have input streams leading to this step!
		if (input.length>0)
		{
			cr = new CheckResult(CheckResultInterface.TYPE_RESULT_OK, Messages.getString("MySQLBulkLoaderMeta.CheckResult.StepReceivingInfoFromOtherSteps"), stepMeta); //$NON-NLS-1$
			remarks.add(cr);
		}
		else
		{
			cr = new CheckResult(CheckResultInterface.TYPE_RESULT_ERROR, Messages.getString("MySQLBulkLoaderMeta.CheckResult.NoInputError"), stepMeta); //$NON-NLS-1$
			remarks.add(cr);
		}
	}

	public SQLStatement getSQLStatements(TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev) throws KettleStepException
	{
		SQLStatement retval = new SQLStatement(stepMeta.getName(), databaseMeta, null); // default: nothing to do!

		if (databaseMeta!=null)
		{
			if (prev!=null && prev.size()>0)
			{
                // Copy the row
                RowMetaInterface tableFields = new RowMeta();

                // Now change the field names
                for (int i=0;i<fieldTable.length;i++)
                {
                    ValueMetaInterface v = prev.searchValueMeta(fieldStream[i]);
                    if (v!=null)
                    {
                        ValueMetaInterface tableField = v.clone();
                        tableField.setName(fieldTable[i]);
                        tableFields.addValueMeta(tableField);
                    }
                    else
                    {
                        throw new KettleStepException("Unable to find field ["+fieldStream[i]+"] in the input rows");
                    }
                }

				if (!Const.isEmpty(tableName))
				{
                    Database db = new Database(databaseMeta);
                    db.shareVariablesWith(transMeta);
					try
					{
						db.connect();

                        String schemaTable = databaseMeta.getQuotedSchemaTableCombination(transMeta.environmentSubstitute(schemaName), 
                        		                                                          transMeta.environmentSubstitute(tableName));                        
						String cr_table = db.getDDL(schemaTable,
													tableFields,
													null,
													false,
													null,
													true
													);

						String cr_index = ""; //$NON-NLS-1$
						String idx_fields[] = null;

						// Key lookup dimensions...
						if (idx_fields!=null && idx_fields.length>0 &&  
								!db.checkIndexExists(transMeta.environmentSubstitute(schemaName), 
										             transMeta.environmentSubstitute(tableName), idx_fields)
						   )
						{
							String indexname = "idx_"+tableName+"_lookup"; //$NON-NLS-1$ //$NON-NLS-2$
							cr_index = db.getCreateIndexStatement(schemaTable, indexname, idx_fields, false, false, false, true);
						}

						String sql = cr_table+cr_index;
						if (sql.length()==0) retval.setSQL(null); else retval.setSQL(sql);
					}
					catch(KettleException e)
					{
						retval.setError(Messages.getString("MySQLBulkLoaderMeta.GetSQL.ErrorOccurred")+e.getMessage()); //$NON-NLS-1$
					}
				}
				else
				{
					retval.setError(Messages.getString("MySQLBulkLoaderMeta.GetSQL.NoTableDefinedOnConnection")); //$NON-NLS-1$
				}
			}
			else
			{
				retval.setError(Messages.getString("MySQLBulkLoaderMeta.GetSQL.NotReceivingAnyFields")); //$NON-NLS-1$
			}
		}
		else
		{
			retval.setError(Messages.getString("MySQLBulkLoaderMeta.GetSQL.NoConnectionDefined")); //$NON-NLS-1$
		}

		return retval;
	}

	public void analyseImpact(List<DatabaseImpact> impact, TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev, String input[], String output[], RowMetaInterface info) throws KettleStepException
    {
        if (prev != null)
        {
            /* DEBUG CHECK THIS */
            // Insert dateMask fields : read/write
            for (int i = 0; i < fieldTable.length; i++)
            {
                ValueMetaInterface v = prev.searchValueMeta(fieldStream[i]);

                DatabaseImpact ii = new DatabaseImpact(DatabaseImpact.TYPE_IMPACT_READ_WRITE, transMeta.getName(), stepMeta.getName(), databaseMeta
                        .getDatabaseName(), transMeta.environmentSubstitute(tableName), fieldTable[i], fieldStream[i], v!=null?v.getOrigin():"?", "", "Type = " + v.toStringMeta()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                impact.add(ii);
            }
        }
	}

	public StepInterface getStep(StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, TransMeta transMeta, Trans trans)
	{
		return new MySQLBulkLoader(stepMeta, stepDataInterface, cnr, transMeta, trans);
	}

	public StepDataInterface getStepData()
	{
		return new MySQLBulkLoaderData();
	}

    public DatabaseMeta[] getUsedDatabaseConnections()
    {
        if (databaseMeta!=null)
        {
            return new DatabaseMeta[] { databaseMeta };
        }
        else
        {
            return super.getUsedDatabaseConnections();
        }
    }

    public RowMetaInterface getRequiredFields(VariableSpace space) throws KettleException
    {
    	String realTableName = space.environmentSubstitute(tableName);
    	String realSchemaName = space.environmentSubstitute(schemaName);

        if (databaseMeta!=null)
        {
            Database db = new Database(databaseMeta);
            try
            {
                db.connect();

                if (!Const.isEmpty(realTableName))
                {
                    String schemaTable = databaseMeta.getQuotedSchemaTableCombination(realSchemaName, realTableName);

                    // Check if this table exists...
                    if (db.checkTableExists(schemaTable))
                    {
                        return db.getTableFields(schemaTable);
                    }
                    else
                    {
                        throw new KettleException(Messages.getString("MySQLBulkLoaderMeta.Exception.TableNotFound"));
                    }
                }
                else
                {
                    throw new KettleException(Messages.getString("MySQLBulkLoaderMeta.Exception.TableNotSpecified"));
                }
            }
            catch(Exception e)
            {
                throw new KettleException(Messages.getString("MySQLBulkLoaderMeta.Exception.ErrorGettingFields"), e);
            }
            finally
            {
                db.disconnect();
            }
        }
        else
        {
            throw new KettleException(Messages.getString("MySQLBulkLoaderMeta.Exception.ConnectionNotDefined"));
        }

    }

    /**
     * @return the schemaName
     */
    public String getSchemaName()
    {
        return schemaName;
    }

    /**
     * @param schemaName the schemaName to set
     */
    public void setSchemaName(String schemaName)
    {
        this.schemaName = schemaName;
    }

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public String getDelimiter() {
		return delimiter;
	}
	
	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}

	public String getEnclosure() {
		return enclosure;
	}
	
	public void setEnclosure(String enclosure) {
		this.enclosure = enclosure;
	}
    
	/**
	 * @return the fifoFileName
	 */
	public String getFifoFileName() {
		return fifoFileName;
	}

	/**
	 * @param fifoFileName the fifoFileName to set
	 */
	public void setFifoFileName(String fifoFileName) {
		this.fifoFileName = fifoFileName;
	}

	/**
	 * @return the replacingData
	 */
	public boolean isReplacingData() {
		return replacingData;
	}

	/**
	 * @param replacingData the replacingData to set
	 */
	public void setReplacingData(boolean replacingData) {
		this.replacingData = replacingData;
	}
	
	public int[] getFieldFormatType() {
		return fieldFormatType;
	}
	
	public void setFieldFormatType(int[] fieldFormatType) {
		this.fieldFormatType = fieldFormatType;
	}

	public static String[] getFieldFormatTypeCodes() {
		return fieldFormatTypeCodes;
	}
	
	public static String[] getFieldFormatTypeDescriptions() {
		return fieldFormatTypeDescriptions;
	}

	public static String getFieldFormatTypeCode(int type) {
		return fieldFormatTypeCodes[type];
	}
	
	public static String getFieldFormatTypeDescription(int type) {
		return fieldFormatTypeDescriptions[type];
	}
	
	public static int getFieldFormatType(String codeOrDescription) {
		for (int i=0;i<fieldFormatTypeCodes.length;i++) {
			if (fieldFormatTypeCodes[i].equalsIgnoreCase(codeOrDescription)) {
				return i;
			}
		}
		for (int i=0;i<fieldFormatTypeDescriptions.length;i++) {
			if (fieldFormatTypeDescriptions[i].equalsIgnoreCase(codeOrDescription)) {
				return i;
			}
		}
		return FIELD_FORMAT_TYPE_OK;
	}

	/**
	 * @return the escapeChar
	 */
	public String getEscapeChar() {
		return escapeChar;
	}

	/**
	 * @param escapeChar the escapeChar to set
	 */
	public void setEscapeChar(String escapeChar) {
		this.escapeChar = escapeChar;
	}

	/**
	 * @return the ignoringErrors
	 */
	public boolean isIgnoringErrors() {
		return ignoringErrors;
	}

	/**
	 * @param ignoringErrors the ignoringErrors to set
	 */
	public void setIgnoringErrors(boolean ignoringErrors) {
		this.ignoringErrors = ignoringErrors;
	}

}
