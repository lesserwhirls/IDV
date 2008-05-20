/*
 * $Id: UrlChooser.java,v 1.40 2007/07/27 13:53:08 jeffmc Exp $
 *
 * Copyright  1997-2004 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package ucar.unidata.idv.chooser;


import org.w3c.dom.Element;

import ucar.unidata.data.DataManager;



import ucar.unidata.idv.*;




import ucar.unidata.ui.ChooserPanel;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.Misc;
import ucar.unidata.util.ObjectListener;

import ucar.unidata.util.PreferenceList;
import ucar.unidata.util.StringUtil;


import ucar.unidata.xml.XmlUtil;

import java.awt.*;
import java.awt.event.*;

import java.beans.PropertyChangeEvent;

import java.beans.PropertyChangeListener;


import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;






/**
 * Allows the user to select a url as a data source
 *
 * @author IDV development team
 * @version $Revision: 1.40 $Date: 2007/07/27 13:53:08 $
 */


public class UrlChooser extends IdvChooser implements ActionListener {

    /** Property name to get the list or urls */
    public static final String PREF_URLLIST = "idv.urllist";

    /** Manages the pull down list of urls */
    private PreferenceList prefList;

    /** The list of urls */
    private JComboBox box;

    /** The text area for multi-line urls */
    private JTextArea textArea;

    /** text scroller */
    private JScrollPane textScroller;


    /** Holds the combo box */
    private JPanel urlPanel;

    /** Holds the text area */
    private JPanel textPanel;

    /** Are we showing the combo box */
    private boolean showBox = true;

    /** Swtich */
    private JButton switchBtn;

    /** panel */
    private GuiUtils.CardLayoutPanel cardLayoutPanel;

    /**
     * Create the <code>UrlChooser</code>
     *
     * @param mgr The <code>IdvChooserManager</code>
     * @param root  The xml root that defines this chooser
     *
     */
    public UrlChooser(IdvChooserManager mgr, Element root) {
        super(mgr, root);
    }


    /**
     * Override base class method so we don't show the Update button.
     *
     * @return The array of button names.
     */
    protected String[] getButtonLabels() {
        return new String[] { getLoadCommandName(), GuiUtils.CMD_HELP};
    }

    /**
     * Get the tooltip for the load button
     *
     * @return The tooltip for the load button
     */
    protected String getLoadToolTip() {
        return "Load the URL";
    }



    /**
     * Create the GUI
     *
     * @return The GUI
     */
    protected JComponent doMakeContents() {
        JComponent dsComp = getDataSourcesComponent();
        dsComp = GuiUtils.inset(GuiUtils.label("Data Source Type: ", dsComp),
                                4);



        prefList = getPreferenceList(PREF_URLLIST);
        JComponent urlButtons = getDefaultButtons();
        textArea     = new JTextArea(5, 30);
        textScroller = new JScrollPane(textArea);
        textScroller.setPreferredSize(new Dimension(200, 100));
        textScroller.setMinimumSize(new Dimension(200, 100));


        box = prefList.createComboBox(CMD_LOAD, this);
        JPanel boxWrapper = GuiUtils.doLayout(new Component[] { box }, 1,
                                GuiUtils.WT_Y, GuiUtils.WT_N);
        boxWrapper.setPreferredSize(new Dimension(200, 40));
        //        GuiUtils.setHFill();
        urlPanel =
            GuiUtils.top(GuiUtils.leftCenter(GuiUtils.rLabel("URL:  "),
                                             boxWrapper));
        textPanel = GuiUtils.leftCenter(GuiUtils.rLabel("URLS:  "),
                                        textScroller);
        switchBtn =
            GuiUtils.makeImageButton("/auxdata/ui/icons/DownDown.gif", this,
                                     "switchFields");

        cardLayoutPanel = new GuiUtils.CardLayoutPanel();

        cardLayoutPanel.addCard(urlPanel);
        cardLayoutPanel.addCard(textPanel);



        JComponent mainContents;
        if (dsComp != null) {
            mainContents =
                GuiUtils.vbox(GuiUtils.left(dsComp),
                              GuiUtils.centerRight(cardLayoutPanel,
                                  GuiUtils.top(switchBtn)), urlButtons);
        } else {
            mainContents =
                GuiUtils.vbox(GuiUtils.centerRight(cardLayoutPanel,
                    GuiUtils.top(switchBtn)), urlButtons);

        }
        mainContents = GuiUtils.inset(mainContents,5);
        setHaveData(true);
        return GuiUtils.top(mainContents);
    }


    /**
     * toggle the combobox with the text area
     */
    public void switchFields() {
        showBox = !showBox;
        if (showBox) {
            cardLayoutPanel.show(urlPanel);
            switchBtn.setIcon(
                GuiUtils.getImageIcon("/auxdata/ui/icons/DownDown.gif"));
        } else {
            cardLayoutPanel.show(textPanel);
            switchBtn.setIcon(
                GuiUtils.getImageIcon("/auxdata/ui/icons/UpUp.gif"));
        }
    }


    /**
     * Wrapper around {@see #loadURLInner()}, showing the wait cursor
     */
    private void loadURL() {
        showWaitCursor();
        loadURLInner();
        showNormalCursor();
    }

    /**
     * Load the url
     */
    private void loadURLInner() {

        String url = "";
        String    dataSourceId = getDataSourceId();
        if (showBox) {
            Object selectedItem = box.getSelectedItem();
            if (selectedItem != null) {
                url = selectedItem.toString().trim();
            }
            if (url.length() == 0 && dataSourceId == null) {
                userMessage("Please specify a url");
                return;
            }
        }

        Hashtable properties   = new Hashtable();
        if (dataSourceId != null) {
            properties.put(DataManager.DATATYPE_ID, dataSourceId);
        }

        if (showBox) {
            if (idv.handleAction(url, properties)) {
                closeChooser();
                prefList.saveState(box);
            }
        } else {
            List urls = StringUtil.split(textArea.getText(), "\n", true,
                                         true);

            if ((urls.size() > 0)
                    && makeDataSource(urls, dataSourceId, properties)) {
                closeChooser();
            }
        }
    }

    /**
     * Handle the action event from the GUI
     */
    public void doLoadInThread() {
        loadURL();
    }



}

