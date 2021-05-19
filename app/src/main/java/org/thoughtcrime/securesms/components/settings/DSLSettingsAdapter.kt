package org.thoughtcrime.securesms.components.settings

import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.annotation.CallSuper
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.MappingAdapter
import org.thoughtcrime.securesms.util.MappingViewHolder
import org.thoughtcrime.securesms.util.ViewUtil

class DSLSettingsAdapter : MappingAdapter() {
  init {
    registerFactory(ClickPreference::class.java, LayoutFactory(::ClickPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(TextPreference::class.java, LayoutFactory(::TextPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(RadioListPreference::class.java, LayoutFactory(::RadioListPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(MultiSelectListPreference::class.java, LayoutFactory(::MultiSelectListPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(ExternalLinkPreference::class.java, LayoutFactory(::ExternalLinkPreferenceViewHolder, R.layout.dsl_preference_item))
    registerFactory(DividerPreference::class.java, LayoutFactory(::DividerPreferenceViewHolder, R.layout.dsl_divider_item))
    registerFactory(SectionHeaderPreference::class.java, LayoutFactory(::SectionHeaderPreferenceViewHolder, R.layout.dsl_section_header))
    registerFactory(SwitchPreference::class.java, LayoutFactory(::SwitchPreferenceViewHolder, R.layout.dsl_switch_preference_item))
    registerFactory(RadioPreference::class.java, LayoutFactory(::RadioPreferenceViewHolder, R.layout.dsl_radio_preference_item))
  }
}

abstract class PreferenceViewHolder<T : PreferenceModel<T>>(itemView: View) : MappingViewHolder<T>(itemView) {
  protected val iconView: ImageView = itemView.findViewById(R.id.icon)
  protected val titleView: TextView = itemView.findViewById(R.id.title)
  protected val summaryView: TextView = itemView.findViewById(R.id.summary)

  @CallSuper
  override fun bind(model: T) {
    listOf(itemView, titleView, summaryView).forEach {
      it.isEnabled = model.isEnabled
    }

    if (model.iconId != -1) {
      iconView.setImageResource(model.iconId)
      iconView.visibility = View.VISIBLE
    } else {
      iconView.setImageDrawable(null)
      iconView.visibility = View.GONE
    }

    val title = model.title?.resolve(context)
    if (title != null) {
      titleView.text = model.title?.resolve(context)
      titleView.visibility = View.VISIBLE
    } else {
      titleView.visibility = View.GONE
    }

    val summary = model.summary?.resolve(context)
    if (summary != null) {
      summaryView.text = summary
      summaryView.visibility = View.VISIBLE

      val spans = (summaryView.text as? Spanned)?.getSpans(0, summaryView.text.length, ClickableSpan::class.java)
      if (spans?.isEmpty() == false) {
        summaryView.movementMethod = LinkMovementMethod.getInstance()
      } else {
        summaryView.movementMethod = null
      }
    } else {
      summaryView.visibility = View.GONE
      summaryView.movementMethod = null
    }
  }
}

class TextPreferenceViewHolder(itemView: View) : PreferenceViewHolder<TextPreference>(itemView)

class ClickPreferenceViewHolder(itemView: View) : PreferenceViewHolder<ClickPreference>(itemView) {
  override fun bind(model: ClickPreference) {
    super.bind(model)
    itemView.setOnClickListener { model.onClick() }
  }
}

class RadioListPreferenceViewHolder(itemView: View) : PreferenceViewHolder<RadioListPreference>(itemView) {
  override fun bind(model: RadioListPreference) {
    super.bind(model)

    summaryView.visibility = View.VISIBLE
    summaryView.text = model.listItems[model.selected]

    itemView.setOnClickListener {
      MaterialAlertDialogBuilder(context)
        .setTitle(model.title.resolve(context))
        .setSingleChoiceItems(model.listItems, model.selected) { dialog, which ->
          model.onSelected(which)
          dialog.dismiss()
        }
        .show()
    }
  }
}

class MultiSelectListPreferenceViewHolder(itemView: View) : PreferenceViewHolder<MultiSelectListPreference>(itemView) {
  override fun bind(model: MultiSelectListPreference) {
    super.bind(model)

    summaryView.visibility = View.VISIBLE
    val summaryText = model.selected
      .mapIndexed { index, isChecked -> if (isChecked) model.listItems[index] else null }
      .filterNotNull()
      .joinToString(", ")

    if (summaryText.isEmpty()) {
      summaryView.setText(R.string.preferences__none)
    } else {
      summaryView.text = summaryText
    }

    val selected = model.selected.copyOf()

    itemView.setOnClickListener {
      MaterialAlertDialogBuilder(context)
        .setTitle(model.title.resolve(context))
        .setMultiChoiceItems(model.listItems, selected) { _, _, _ ->
          // Intentionally empty
        }
        .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
        .setPositiveButton(android.R.string.ok) { d, _ ->
          model.onSelected(selected)
          d.dismiss()
        }
        .show()
    }
  }
}

class SwitchPreferenceViewHolder(itemView: View) : PreferenceViewHolder<SwitchPreference>(itemView) {

  private val switchWidget: SwitchMaterial = itemView.findViewById(R.id.switch_widget)

  override fun bind(model: SwitchPreference) {
    super.bind(model)
    switchWidget.isEnabled = model.isEnabled
    switchWidget.isChecked = model.isChecked
    itemView.setOnClickListener {
      model.onClick()
    }
  }
}

class RadioPreferenceViewHolder(itemView: View) : PreferenceViewHolder<RadioPreference>(itemView) {

  private val radioButton: RadioButton = itemView.findViewById(R.id.radio_widget)

  override fun bind(model: RadioPreference) {
    super.bind(model)
    radioButton.isChecked = model.isChecked
    itemView.setOnClickListener {
      model.onClick()
    }
  }
}

class ExternalLinkPreferenceViewHolder(itemView: View) : PreferenceViewHolder<ExternalLinkPreference>(itemView) {
  override fun bind(model: ExternalLinkPreference) {
    super.bind(model)

    val externalLinkIcon = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_open_20))
    externalLinkIcon.setBounds(0, 0, ViewUtil.dpToPx(20), ViewUtil.dpToPx(20))

    if (ViewUtil.isLtr(itemView)) {
      titleView.setCompoundDrawables(null, null, externalLinkIcon, null)
    } else {
      titleView.setCompoundDrawables(externalLinkIcon, null, null, null)
    }

    itemView.setOnClickListener { CommunicationActions.openBrowserLink(itemView.context, itemView.context.getString(model.linkId)) }
  }
}

class DividerPreferenceViewHolder(itemView: View) : MappingViewHolder<DividerPreference>(itemView) {
  override fun bind(model: DividerPreference) = Unit
}

class SectionHeaderPreferenceViewHolder(itemView: View) : MappingViewHolder<SectionHeaderPreference>(itemView) {

  private val sectionHeader: TextView = itemView.findViewById(R.id.section_header)

  override fun bind(model: SectionHeaderPreference) {
    sectionHeader.text = model.title.resolve(context)
  }
}
