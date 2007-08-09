 /**********************************************************************
 **                                                                   **
 **               This code belongs to the KETTLE project.            **
 **                                                                   **
 ** Kettle, from version 2.2 on, is released into the public domain   **
 ** under the Lesser GNU Public License (LGPL).                       **
 **                                                                   **
 ** For more details, please read the document LICENSE.txt, included  **
 ** in this project                                                   **
 **                                                                   **
 ** http://www.kettle.be                                              **
 ** info@kettle.be                                                    **
 **                                                                   **
 **********************************************************************/


package org.pentaho.di.trans.steps.cubeinput;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.Counter;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleFileException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepCategory;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.w3c.dom.Node;

/*
 * Created on 2-jun-2003
 *
 */
@Step(name="CubeInput",image="ui/images/CIP.png",tooltip="BaseStep.TypeTooltipDesc.Cubeinput",description="BaseStep.TypeLongDesc.CubeInput",
		category=StepCategory.INPUT)
public class CubeInputMeta extends BaseStepMeta implements StepMetaInterface
{
	private String filename;
	private int rowLimit;

	public CubeInputMeta()
	{
		super(); // allocate BaseStepMeta
	}

	public void loadXML(Node stepnode, List<DatabaseMeta> databases, Map<String,Counter> counters)
		throws KettleXMLException
	{
		readData(stepnode);
	}

	/**
	 * @return Returns the filename.
	 */
	public String getFilename()
	{
		return filename;
	}
	
	/**
	 * @param filename The filename to set.
	 */
	public void setFilename(String filename)
	{
		this.filename = filename;
	}
	
	/**
	 * @param rowLimit The rowLimit to set.
	 */
	public void setRowLimit(int rowLimit)
	{
		this.rowLimit = rowLimit;
	}
	
	/**
	 * @return Returns the rowLimit.
	 */
	public int getRowLimit()
	{
		return rowLimit;
	}
	
	public Object clone()
	{
		CubeInputMeta retval = (CubeInputMeta)super.clone();
		return retval;
	}
	
	private void readData(Node stepnode)
		throws KettleXMLException
	{
		try
		{
			filename  = XMLHandler.getTagValue(stepnode, "file", "name"); //$NON-NLS-1$ //$NON-NLS-2$
			rowLimit  = Const.toInt( XMLHandler.getTagValue(stepnode, "limit"), 0); //$NON-NLS-1$
		}
		catch(Exception e)
		{
			throw new KettleXMLException(Messages.getString("CubeInputMeta.Exception.UnableToLoadStepInfo"), e); //$NON-NLS-1$
		}
	}

	public void setDefault()
	{
		filename = ""; //$NON-NLS-1$
		rowLimit   = 0;
	}
	
	public void getFields(RowMetaInterface r, String name, RowMetaInterface[] info, StepMeta nextStep, VariableSpace space)
		throws KettleStepException
	{
		GZIPInputStream fis = null;
		DataInputStream dis = null;
		try
		{
			File f = new File(filename);
			fis = new GZIPInputStream(new FileInputStream(f));
			dis = new DataInputStream(fis);
	
			RowMetaInterface add = new RowMeta(dis);		
				
			if (add==null) return;
			for (int i=0;i<add.size();i++)
			{
				add.getValueMeta(i).setOrigin(name);
			}
			r.mergeRowMeta(add);
		}
		catch(KettleFileException kfe)
		{
			throw new KettleStepException(Messages.getString("CubeInputMeta.Exception.UnableToReadMetaData"), kfe); //$NON-NLS-1$
		}
		catch(IOException e)
		{
			throw new KettleStepException(Messages.getString("CubeInputMeta.Exception.ErrorOpeningOrReadingCubeFile"), e); //$NON-NLS-1$
		}
		finally
		{
			try
			{
				if (fis!=null) fis.close();
				if (dis!=null) dis.close();
			}
			catch(IOException ioe)
			{
				throw new KettleStepException(Messages.getString("CubeInputMeta.Exception.UnableToCloseCubeFile"), ioe); //$NON-NLS-1$
			}
		}
	}
	
	public String getXML()
	{
        StringBuffer retval = new StringBuffer();
		
		retval.append("    <file>"+Const.CR); //$NON-NLS-1$
		retval.append("      "+XMLHandler.addTagValue("name", filename)); //$NON-NLS-1$ //$NON-NLS-2$
		retval.append("      </file>"+Const.CR); //$NON-NLS-1$
		retval.append("    "+XMLHandler.addTagValue("limit",    rowLimit)); //$NON-NLS-1$ //$NON-NLS-2$

		return retval.toString();
	}
	public void readRep(Repository rep, long id_step, List<DatabaseMeta> databases, Map<String,Counter> counters)
		throws KettleException
	{
		try
		{
			filename         =      rep.getStepAttributeString (id_step, "file_name"); //$NON-NLS-1$
			rowLimit         = (int)rep.getStepAttributeInteger(id_step, "limit"); //$NON-NLS-1$
		}
		catch(Exception e)
		{
			throw new KettleException(Messages.getString("CubeInputMeta.Exception.UnexpectedErrorWhileReadingStepInfo"), e); //$NON-NLS-1$
		}
	}

	public void saveRep(Repository rep, long id_transformation, long id_step)
		throws KettleException
	{
		try
		{
			rep.saveStepAttribute(id_transformation, id_step, "file_name",   filename); //$NON-NLS-1$
			rep.saveStepAttribute(id_transformation, id_step, "limit",       rowLimit); //$NON-NLS-1$
		}
		catch(KettleException e)
		{
			throw new KettleException(Messages.getString("CubeInputMeta.Exception.UnableToSaveStepInfo")+id_step, e); //$NON-NLS-1$
		}
	}

	public void check(List<CheckResultInterface> remarks, TransMeta transmeta, StepMeta stepinfo, RowMetaInterface prev, String input[], String output[], RowMetaInterface info)
	{
		CheckResult cr;
		
		cr = new CheckResult(CheckResult.TYPE_RESULT_COMMENT, Messages.getString("CubeInputMeta.CheckResult.FileSpecificationsNotChecked"), stepinfo); //$NON-NLS-1$
		remarks.add(cr);
	}
	
	public StepInterface getStep(StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, TransMeta tr, Trans trans)
	{
		return new CubeInput(stepMeta, stepDataInterface, cnr, tr, trans);
	}

	public StepDataInterface getStepData()
	{
		return new CubeInputData();
	}

}
