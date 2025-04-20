package com.github.sidedev.sidekick.toolWindow.components

import com.github.sidedev.sidekick.api.Subflow
import com.github.sidedev.sidekick.toolWindow.FlowActionComponent
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JPanel

class SubflowSection(
    private val subflow: Subflow
) : JBPanel<SubflowSection>(BorderLayout()) {
    
    private val contentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty()
    }
    
    private val accordionSection: AccordionSection
    
    init {
        // Create header text combining name and description
        val headerText = buildString {
            append(subflow.name)
            subflow.description?.let {
                append(" - ")
                append(it)
            }
        }
        
        // Create accordion section with the content panel
        accordionSection = AccordionSection(
            title = headerText,
            content = contentPanel,
            initiallyExpanded = false
        )
        
        add(accordionSection, BorderLayout.CENTER)
    }
    
    fun addFlowAction(flowActionComponent: FlowActionComponent) {
        contentPanel.add(flowActionComponent)
        contentPanel.revalidate()
        contentPanel.repaint()
    }
    
    fun setExpanded(expand: Boolean) {
        accordionSection.setExpanded(expand)
    }
    
    fun isExpanded(): Boolean = accordionSection.isExpanded()
}