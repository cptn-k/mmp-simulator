/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jp.riken.aics.mmpsim;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.event.ChangeListener;
import net.hkhandan.gui.GridCanvas;
import net.hkhandan.gui.CurveCanvas2D;
import net.hkhandan.math.IntPair;

/**
 *
 * @author hamed
 */
public class SimulatorUIForm extends javax.swing.JFrame implements ActionListener, ChangeListener {

    public static interface ActionListener {
        void scenarioSelected(int index);
        void partitioningMethodSelected(int index);
        void alternativePartitioningSelected(int index);
        void dimensionsChanged(int nRows, int nCols);
        void synchroinzationSchemeSelected(int index);
        void boundryBufferingSettingsChanged(boolean enabled);
        void speedChanged(int speed);
        void cellSelected(int row, int col);
        void start();
        void pause();
        void resume();
        void reset();
    }
    
    public static enum State {
        STOPPED,
        RUNNING,
        PAUSED
    };
    
    public static enum CellState {
        INIT,
        IDLE,
        ACTIVE,
        BLOCKED,
        FAULT, 
        IO
    };
    
    public static enum CurveName {
        PROGRESS,
        COMPUTE_TIME,
        IDLE_TIME,
        NETWORK_TRAFFIC_TIME,
    }

    private class DelayedStatusCleaner extends Thread {
        boolean cancelled = false;
        boolean finished = false;
        
        @Override
        public void run() {
            try {
                sleep(9000);
            } catch (InterruptedException ex) {
                throw new Error(this.getName() + " thread interrupted.");
            }
            
            finished = true;
            
            if(cancelled) 
                return;
            
            lblStatus.setText(" ");            
        }
        
        public void cancel() {
            cancelled = true;
        }
    }
    
    private final static Color LIGHT_GREEN = new Color(200, 255, 200);
    
    private final ActionListener actionListener;
    
    private boolean repaintThreadStopFlag = false;
    private StringBuffer messageTextBuffer = new StringBuffer();
    private DelayedStatusCleaner delayedStatusCleaner = null;
    private boolean noActionCallback = false;
    private State state = State.STOPPED;
    
    private final Thread uiRepaintTimer = new Thread("Grid Repaint Timer") {
        @Override
        public void run() {
            while(true) {
                gridCanvas.repaint();
                for(CurveCanvas2D canvas:curveCanvases) {
                    canvas.repaint();
                }
                try {
                    sleep(33);
                } catch (InterruptedException ex) {
                    throw new Error(this.getName() + " thread interrupted.");
                }
                if(repaintThreadStopFlag)
                    return;
            }
        }
    };
    
    private final MouseListener gridCanvasMouseListener = new MouseListener() {
        @Override
        public void mouseClicked(MouseEvent e) {
            IntPair loc = gridCanvas.getCellAt(e.getX(), e.getY());
            if(loc != null)
                actionListener.cellSelected(loc.y, loc.x);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            // Nothing
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            // Nothing
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            // Nothing
        }

        @Override
        public void mouseExited(MouseEvent e) {
            // Nothing
        }
    };
            
    /**
     * Creates new form SimulatorUIForm
     * @param actionListener
     */
    public SimulatorUIForm(ActionListener actionListener) {        
        initComponents();
        this.actionListener = actionListener;
        
        gridCanvas = new GridCanvas(0, 0, 4);
        gridCanvas.addMouseListener(gridCanvasMouseListener);
        
        GridCanvas.Cell defaultCell = new GridCanvas.Cell();
        defaultCell.background = Color.WHITE;
        defaultCell.foreground = Color.GRAY;
        defaultCell.shape = GridCanvas.Shape.FILLED_RECT;
        gridCanvas.setDefault(defaultCell);
        
        scrlGridView.setViewportView(gridCanvas);
        
        curveCanvases = new CurveCanvas2D[CurveName.values().length];
        for(int i = curveCanvases.length - 1; i >= 0; i--) {
            curveCanvases[i] = new CurveCanvas2D();            
        }
        
        pnlProgress.add(curveCanvases[CurveName.PROGRESS.ordinal()], BorderLayout.CENTER);
        pnlComputeTime.add(curveCanvases[CurveName.COMPUTE_TIME.ordinal()], BorderLayout.CENTER);
        pnlWaitTime.add(curveCanvases[CurveName.IDLE_TIME.ordinal()], BorderLayout.CENTER);
        pnlNetTraffic.add(curveCanvases[CurveName.NETWORK_TRAFFIC_TIME.ordinal()], BorderLayout.CENTER);
        
        uiRepaintTimer.start();
    }
    
    public void clearGroups() {
        gridCanvas.clearGroups();
        gridCanvas.repaint();
        gridCanvas.revalidate();
    }
    
    public void createGroup(GridDivision2D bounds, int spacing, boolean draw) {
        gridCanvas.createGroup(bounds.rowOffset,
                bounds.colOffset,
                bounds.rowOffset + bounds.nRows,
                bounds.colOffset + bounds.nCols, spacing, draw);
        gridCanvas.repaint();
        gridCanvas.revalidate();
    }
    
    public void updateCell(int row, int col, float f, CellState state) {
        switch(state) {
            case INIT:
                gridCanvas.setForeground(col, row, LIGHT_GREEN);
                break;
                
            case IDLE:
                gridCanvas.setForeground(col, row, new Color(1-f, 1-f, 1-f));
                break;
                
            case ACTIVE:
                if(f < 0.5)
                    gridCanvas.setForeground(col, row, Color.BLACK);
                else
                    gridCanvas.setForeground(col, row, Color.WHITE);                
                break;
                
            case IO:
                gridCanvas.setForeground(col, row, Color.BLUE);
                break;
                
            case BLOCKED:
                gridCanvas.setForeground(col, row, Color.RED);
                break;
                
            case FAULT:
                gridCanvas.addCircle(col, row, gridCanvas.getCellSize() * 2, Color.RED);
                break;
        }
    }
    
    public void hiliteCell(int row, int col, Color c) {
        gridCanvas.addCircle(col, row, gridCanvas.getCellSize() * 2, c);      
        gridCanvas.repaint();
    }
    
    public void removeCellHitite(int row, int col) {
        gridCanvas.removeCirlcesAt(col, row);
        gridCanvas.repaint();
    }
    
    public void connect(int srcRow, int srcCol, int dstRow, int dstCol, boolean emphesize) {
        if(emphesize) {
            gridCanvas.createConnection(srcCol, srcRow, dstCol, dstRow, Color.RED, GridCanvas.StrokeStyle.NORMAL);
        } else {
            gridCanvas.createConnection(srcCol, srcRow, dstCol, dstRow, Color.BLUE, GridCanvas.StrokeStyle.NORMAL);
        }
    }
        
    public void clear() {
        gridCanvas.clearConnections();
        gridCanvas.clearCircles();
        gridCanvas.repaint();
    }
    
    public void putPlot(CurveName name, int index, double[] x, double[] y, int count) {
        curveCanvases[name.ordinal()].putPlot(x, y, count, name.toString(), null, index, true);
    }
    
    public void clearPlots() {
        for(CurveCanvas2D curveCanvas:curveCanvases) {
            curveCanvas.clear();
            curveCanvas.repaint();
        }
    }
    
    public void setProgress(double p) {
        progressBar.setValue((int)Math.round(p*1000));
    }
    
    public void setGridSize(int nRows, int nCols) {
        noActionCallback = true;
        gridCanvas.setGridSize(nCols, nRows);
        spinnerRows.setValue(new Integer(nRows));
        spinnerColumns.setValue(new Integer(nCols));
        noActionCallback = false;
        gridCanvas.revalidate();
    }

    public void showMessage(String message) {
        System.out.println(message);
        messageTextBuffer.append(message).append("\n\n");
        txtMessages.setText(messageTextBuffer.toString());
        lblStatus.setText(message);
        
        if(delayedStatusCleaner != null)
            delayedStatusCleaner.cancel();
        
        delayedStatusCleaner = new DelayedStatusCleaner();
        delayedStatusCleaner.start();
    }
    
    private void setItems(JComboBox cmbBox, Object[] items) {
        cmbBox.setModel(new DefaultComboBoxModel(items));
    }
    
    private void setSelectedIndex(JComboBox cmbBox, int index) {
        if(index >= cmbBox.getModel().getSize())
            return;
        
        noActionCallback = true;
        cmbBox.setSelectedIndex(index);
        noActionCallback = false;
    }
    
    public void setScenarioNames(Object[] items) {
        setItems(cmbScenario, items);
    }
    
    public void setSelectedScenario(int index) {
        setSelectedIndex(cmbScenario, index);
    }
    
    public void setPartioningMethodNames(Object[] items) {        
        setItems(cmbPartitioningMethod, items);
    }
    
    public void setSelectedPartitioningMethod(int index) {
        setSelectedIndex(cmbPartitioningMethod, index);
    }
    
    public void setAlternativePartitionings(Object[] items) {
        setItems(cmbAlternativePatitioning, items);
    }
    
    public void setSelectedAlternativePartitioning(int index) {
        setSelectedIndex(cmbAlternativePatitioning, index);
    }
    
    public void setSynchronizationMethodNames(Object[] items) {
        setItems(cmbSynchronization, items);
    }
    
    public void setSelectedSynchronizationMethod(int index) {
        setSelectedIndex(cmbSynchronization, index);
    }
    
    public void setBoundaryBuffering(boolean enabled) {
        noActionCallback = true;
        chkBoundaryBuffering.setSelected(enabled);
        noActionCallback = false;
    }
    
    public void setState(State state) {
        this.state = state;
        enableConfiguration(state == State.STOPPED);
        switch(state) {
            case STOPPED:
                jButton1.setText("Start");
                break;
                
            case PAUSED:
                jButton1.setText("Resume");
                break;
                
            case RUNNING:
                jButton1.setText("Pause");
                break;
        }
    }
    
    private void enableConfiguration(boolean enabled) {
        cmbScenario.setEnabled(enabled);
        cmbPartitioningMethod.setEnabled(enabled);
        cmbAlternativePatitioning.setEnabled(enabled);
        cmbSynchronization.setEnabled(enabled);
        spinnerColumns.setEnabled(enabled);
        spinnerRows.setEnabled(enabled);
        chkBoundaryBuffering.setEnabled(enabled);
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane2 = new javax.swing.JScrollPane();
        jPanel4 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        cmbScenario = new javax.swing.JComboBox();
        spinnerRows = new javax.swing.JSpinner();
        jLabel2 = new javax.swing.JLabel();
        spinnerColumns = new javax.swing.JSpinner();
        btnEditModel = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        cmbPartitioningMethod = new javax.swing.JComboBox();
        jLabel7 = new javax.swing.JLabel();
        cmbSynchronization = new javax.swing.JComboBox();
        chkBoundaryBuffering = new javax.swing.JCheckBox();
        jLabel6 = new javax.swing.JLabel();
        cmbAlternativePatitioning = new javax.swing.JComboBox();
        jPanel3 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        jSlider1 = new javax.swing.JSlider();
        jButton1 = new javax.swing.JButton();
        jButton2 = new javax.swing.JButton();
        jPanel6 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        cmbCellSize = new javax.swing.JComboBox();
        jButton3 = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        tabbedPane = new javax.swing.JTabbedPane();
        pnlProgress = new javax.swing.JPanel();
        pnlComputeTime = new javax.swing.JPanel();
        pnlWaitTime = new javax.swing.JPanel();
        pnlNetTraffic = new javax.swing.JPanel();
        scrlTxtMessages = new javax.swing.JScrollPane();
        txtMessages = new javax.swing.JTextArea();
        jPanel2 = new javax.swing.JPanel();
        lblStatus = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        pnlGridView = new javax.swing.JPanel();
        scrlGridView = new javax.swing.JScrollPane();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setPreferredSize(new java.awt.Dimension(800, 600));

        jScrollPane2.setBorder(null);
        jScrollPane2.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane2.setBounds(new java.awt.Rectangle(0, 0, 250, 0));
        jScrollPane2.setMaximumSize(new java.awt.Dimension(250, 32767));
        jScrollPane2.setMinimumSize(new java.awt.Dimension(250, 5));

        jPanel4.setMinimumSize(new java.awt.Dimension(215, 560));
        jPanel4.setPreferredSize(new java.awt.Dimension(250, 660));
        jPanel4.setSize(new java.awt.Dimension(250, 600));
        jPanel4.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("Scenario"));
        jPanel1.setMaximumSize(new java.awt.Dimension(200, 32767));
        jPanel1.setPreferredSize(new java.awt.Dimension(225, 400));
        jPanel1.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel1.setText("Preset");
        jPanel1.add(jLabel1, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 20, -1, -1));

        cmbScenario.setMaximumSize(new java.awt.Dimension(205, 32767));
        cmbScenario.addActionListener(this);
        jPanel1.add(cmbScenario, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 40, 205, -1));

        spinnerRows.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(0), Integer.valueOf(0), null, Integer.valueOf(1)));
        spinnerRows.addChangeListener(this);
        jPanel1.add(spinnerRows, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 140, 89, -1));

        jLabel2.setText("Rows");
        jPanel1.add(jLabel2, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 120, -1, -1));

        spinnerColumns.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(0), Integer.valueOf(0), null, Integer.valueOf(1)));
        spinnerColumns.addChangeListener(this);
        jPanel1.add(spinnerColumns, new org.netbeans.lib.awtextra.AbsoluteConstraints(110, 140, 89, -1));

        btnEditModel.setText("Edit Model");
        btnEditModel.setEnabled(false);
        btnEditModel.addActionListener(this);
        jPanel1.add(btnEditModel, new org.netbeans.lib.awtextra.AbsoluteConstraints(50, 80, 110, -1));

        jLabel3.setText("Columns");
        jPanel1.add(jLabel3, new org.netbeans.lib.awtextra.AbsoluteConstraints(110, 120, -1, -1));

        jLabel4.setText("Partitioning Algoritm");
        jPanel1.add(jLabel4, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 180, -1, -1));

        cmbPartitioningMethod.setMaximumSize(new java.awt.Dimension(205, 32767));
        cmbPartitioningMethod.addActionListener(this);
        jPanel1.add(cmbPartitioningMethod, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 200, 205, -1));

        jLabel7.setText("Synchronization Scheme");
        jPanel1.add(jLabel7, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 300, -1, -1));

        cmbSynchronization.setMaximumSize(new java.awt.Dimension(205, 32767));
        cmbSynchronization.addActionListener(this);
        jPanel1.add(cmbSynchronization, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 320, 205, -1));

        chkBoundaryBuffering.setText("Enable boundary buffering");
        chkBoundaryBuffering.addActionListener(this);
        jPanel1.add(chkBoundaryBuffering, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 360, -1, -1));

        jLabel6.setText("Alternative Partitionings");
        jPanel1.add(jLabel6, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 240, -1, -1));

        cmbAlternativePatitioning.setMaximumSize(new java.awt.Dimension(205, 32767));
        cmbAlternativePatitioning.addActionListener(this);
        jPanel1.add(cmbAlternativePatitioning, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 260, 205, -1));

        jPanel4.add(jPanel1);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("Control"));
        jPanel3.setPreferredSize(new java.awt.Dimension(225, 94));
        jPanel3.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel8.setText("Speed:");
        jPanel3.add(jLabel8, new org.netbeans.lib.awtextra.AbsoluteConstraints(12, 24, -1, -1));

        jSlider1.addChangeListener(this);
        jPanel3.add(jSlider1, new org.netbeans.lib.awtextra.AbsoluteConstraints(59, 18, 160, -1));

        jButton1.setText("Start");
        jButton1.addActionListener(this);
        jPanel3.add(jButton1, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 50, 120, -1));

        jButton2.setText("Stop");
        jButton2.addActionListener(this);
        jPanel3.add(jButton2, new org.netbeans.lib.awtextra.AbsoluteConstraints(130, 50, -1, -1));

        jPanel4.add(jPanel3);

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder("Appearance"));
        jPanel6.setPreferredSize(new java.awt.Dimension(225, 140));
        jPanel6.setSize(new java.awt.Dimension(225, 94));
        jPanel6.setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel5.setText("Cell Size");
        jPanel6.add(jLabel5, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 30, -1, -1));

        cmbCellSize.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "Dots", "Small", "Medium", "Big" }));
        cmbCellSize.setSelectedIndex(1);
        cmbCellSize.addActionListener(this);
        jPanel6.add(cmbCellSize, new org.netbeans.lib.awtextra.AbsoluteConstraints(10, 50, 200, -1));

        jButton3.setText("Clear Graphs");
        jButton3.addActionListener(this);
        jPanel6.add(jButton3, new org.netbeans.lib.awtextra.AbsoluteConstraints(40, 90, -1, -1));

        jPanel4.add(jPanel6);

        jScrollPane2.setViewportView(jPanel4);

        getContentPane().add(jScrollPane2, java.awt.BorderLayout.EAST);

        jPanel5.setLayout(new java.awt.BorderLayout());

        tabbedPane.setMinimumSize(new java.awt.Dimension(105, 200));

        pnlProgress.setPreferredSize(new java.awt.Dimension(0, 160));
        pnlProgress.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab("Progress", pnlProgress);

        pnlComputeTime.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab("Compute Time", pnlComputeTime);

        pnlWaitTime.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab("Wait Time", pnlWaitTime);

        pnlNetTraffic.setLayout(new java.awt.BorderLayout());
        tabbedPane.addTab("Traffic Time", pnlNetTraffic);

        scrlTxtMessages.setBorder(null);

        txtMessages.setEditable(false);
        txtMessages.setBackground(javax.swing.UIManager.getDefaults().getColor("Label.background"));
        txtMessages.setColumns(20);
        txtMessages.setRows(5);
        scrlTxtMessages.setViewportView(txtMessages);

        tabbedPane.addTab("Messages", scrlTxtMessages);

        jPanel5.add(tabbedPane, java.awt.BorderLayout.NORTH);

        jPanel2.setLayout(new java.awt.BorderLayout());

        lblStatus.setText("///");
        lblStatus.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 3, 10));
        jPanel2.add(lblStatus, java.awt.BorderLayout.CENTER);

        progressBar.setMaximum(1000);
        jPanel2.add(progressBar, java.awt.BorderLayout.LINE_END);

        jPanel5.add(jPanel2, java.awt.BorderLayout.CENTER);

        getContentPane().add(jPanel5, java.awt.BorderLayout.SOUTH);

        pnlGridView.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 8, 1, 1));
        pnlGridView.setLayout(new java.awt.BorderLayout());
        pnlGridView.add(scrlGridView, java.awt.BorderLayout.CENTER);

        getContentPane().add(pnlGridView, java.awt.BorderLayout.CENTER);

        pack();
    }

    // Code for dispatching events from components to event handlers.

    public void actionPerformed(java.awt.event.ActionEvent evt) {
        if (evt.getSource() == cmbScenario) {
            SimulatorUIForm.this.cmbScenarioActionPerformed(evt);
        }
        else if (evt.getSource() == btnEditModel) {
            SimulatorUIForm.this.btnEditModelActionPerformed(evt);
        }
        else if (evt.getSource() == cmbPartitioningMethod) {
            SimulatorUIForm.this.cmbPartitioningMethodActionPerformed(evt);
        }
        else if (evt.getSource() == cmbSynchronization) {
            SimulatorUIForm.this.cmbSynchronizationActionPerformed(evt);
        }
        else if (evt.getSource() == chkBoundaryBuffering) {
            SimulatorUIForm.this.chkBoundaryBufferingActionPerformed(evt);
        }
        else if (evt.getSource() == cmbAlternativePatitioning) {
            SimulatorUIForm.this.cmbAlternativePatitioningActionPerformed(evt);
        }
        else if (evt.getSource() == jButton1) {
            SimulatorUIForm.this.jButton1ActionPerformed(evt);
        }
        else if (evt.getSource() == jButton2) {
            SimulatorUIForm.this.jButton2ActionPerformed(evt);
        }
        else if (evt.getSource() == cmbCellSize) {
            SimulatorUIForm.this.cmbCellSizeActionPerformed(evt);
        }
        else if (evt.getSource() == jButton3) {
            SimulatorUIForm.this.jButton3ActionPerformed(evt);
        }
    }

    public void stateChanged(javax.swing.event.ChangeEvent evt) {
        if (evt.getSource() == spinnerRows) {
            SimulatorUIForm.this.spinnerRowsStateChanged(evt);
        }
        else if (evt.getSource() == spinnerColumns) {
            SimulatorUIForm.this.spinnerColumnsStateChanged(evt);
        }
        else if (evt.getSource() == jSlider1) {
            SimulatorUIForm.this.jSlider1StateChanged(evt);
        }
    }// </editor-fold>//GEN-END:initComponents

    private void chkBoundaryBufferingActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkBoundaryBufferingActionPerformed
        if(noActionCallback)
            return;
        actionListener.boundryBufferingSettingsChanged(chkBoundaryBuffering.isSelected());
    }//GEN-LAST:event_chkBoundaryBufferingActionPerformed

    private void cmbSynchronizationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbSynchronizationActionPerformed
        if(noActionCallback)
            return;
        actionListener.synchroinzationSchemeSelected(cmbSynchronization.getSelectedIndex());
    }//GEN-LAST:event_cmbSynchronizationActionPerformed

    private void spinnerRowsStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerRowsStateChanged
        if(noActionCallback)
            return;
        actionListener.dimensionsChanged((Integer)spinnerRows.getModel().getValue(), 
                (Integer)spinnerColumns.getModel().getValue());
    }//GEN-LAST:event_spinnerRowsStateChanged

    private void spinnerColumnsStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerColumnsStateChanged
        if(noActionCallback)
            return;
        actionListener.dimensionsChanged((Integer)spinnerRows.getModel().getValue(), 
                (Integer)spinnerColumns.getModel().getValue());
    }//GEN-LAST:event_spinnerColumnsStateChanged

    private void btnEditModelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnEditModelActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnEditModelActionPerformed

    private void cmbScenarioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbScenarioActionPerformed
        if(noActionCallback)
            return;
        actionListener.scenarioSelected(cmbScenario.getSelectedIndex());
    }//GEN-LAST:event_cmbScenarioActionPerformed

    private void cmbPartitioningMethodActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbPartitioningMethodActionPerformed
        if(noActionCallback)
            return;
        actionListener.partitioningMethodSelected(cmbPartitioningMethod.getSelectedIndex());
    }//GEN-LAST:event_cmbPartitioningMethodActionPerformed

    private void cmbAlternativePatitioningActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbAlternativePatitioningActionPerformed
        if(noActionCallback)
            return;
        actionListener.alternativePartitioningSelected(cmbAlternativePatitioning.getSelectedIndex());
    }//GEN-LAST:event_cmbAlternativePatitioningActionPerformed

    private void jSlider1StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_jSlider1StateChanged
        if(noActionCallback)
            return;
        actionListener.speedChanged(jSlider1.getValue());
    }//GEN-LAST:event_jSlider1StateChanged

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        switch(state) {
            case STOPPED:
                setState(State.RUNNING);
                actionListener.start();
                break;
                
            case RUNNING:
                setState(State.PAUSED);
                actionListener.pause();
                break;
                
            case PAUSED:
                setState(State.RUNNING);
                actionListener.resume();
                break;
        }
    }//GEN-LAST:event_jButton1ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        setState(State.STOPPED);
        actionListener.reset();
    }//GEN-LAST:event_jButton2ActionPerformed

    private void cmbCellSizeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cmbCellSizeActionPerformed
        switch(cmbCellSize.getSelectedIndex()) {
            case 0:
                gridCanvas.setCellSpacing(0);
                gridCanvas.setCellSize(1);
                break;
                
            case 1:
                gridCanvas.setCellSpacing(1);
                gridCanvas.setCellSize(4);
                break;
                
            case 2:
                gridCanvas.setCellSpacing(1);
                gridCanvas.setCellSize(6);
                break;
                
            case 3:
                gridCanvas.setCellSpacing(1);
                gridCanvas.setCellSize(9);
                break;
        }
    }//GEN-LAST:event_cmbCellSizeActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        clearPlots();
    }//GEN-LAST:event_jButton3ActionPerformed
    
    private GridCanvas gridCanvas;
    private CurveCanvas2D[] curveCanvases;
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnEditModel;
    private javax.swing.JCheckBox chkBoundaryBuffering;
    private javax.swing.JComboBox cmbAlternativePatitioning;
    private javax.swing.JComboBox cmbCellSize;
    private javax.swing.JComboBox cmbPartitioningMethod;
    private javax.swing.JComboBox cmbScenario;
    private javax.swing.JComboBox cmbSynchronization;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSlider jSlider1;
    private javax.swing.JLabel lblStatus;
    private javax.swing.JPanel pnlComputeTime;
    private javax.swing.JPanel pnlGridView;
    private javax.swing.JPanel pnlNetTraffic;
    private javax.swing.JPanel pnlProgress;
    private javax.swing.JPanel pnlWaitTime;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JScrollPane scrlGridView;
    private javax.swing.JScrollPane scrlTxtMessages;
    private javax.swing.JSpinner spinnerColumns;
    private javax.swing.JSpinner spinnerRows;
    private javax.swing.JTabbedPane tabbedPane;
    private javax.swing.JTextArea txtMessages;
    // End of variables declaration//GEN-END:variables
}
