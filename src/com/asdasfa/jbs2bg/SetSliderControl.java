package com.asdasfa.jbs2bg;

import java.io.IOException;

import com.asdasfa.jbs2bg.data.SliderPreset.SetSlider;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;

/**
 * 
 * @author Totiman
 */
public class SetSliderControl extends VBox {
	
	private Main main;
	private SetSlider setSlider;
	
	@FXML
	public CheckBox cbEnabled;
	@FXML
	public TextField tfBgFormat;
	@FXML
	public TextField tfMin;
	@FXML
	public TextField tfMax;
	@FXML
	public Slider sldMin;
	@FXML
	public Slider sldMax;
	
	private MyChangeListener mclMin;
	private MyChangeListener mclMax;
	
	public SetSliderControl(Main main, SetSlider setSlider) {
		this.main = main;
		this.setSlider = setSlider;
		
		FXMLLoader loader = new FXMLLoader(getClass().getResource("setslider_control.fxml"));
		loader.setRoot(this);
		loader.setController(this);
		
		try {
			loader.load();
			getStylesheets().add(main.style);
			setPrefWidth(320);
			setPrefHeight(140);
			
			tfBgFormat.setText(setSlider.toText());
			sldMin.setValue(setSlider.getPctMin());
			sldMax.setValue(setSlider.getPctMax());
			tfMin.setText(setSlider.getPctMin() + "%");
			tfMax.setText(setSlider.getPctMax() + "%");
			
			mclMin = new MyChangeListener() {
				@Override
				protected void doChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
					int v = newValue.intValue();
					int vMax = (int) sldMax.getValue();
					if (v > vMax) {
						v = vMax;
						sldMin.setValue(v);
					}
					setSlider.setPctMin(v);
					
					tfBgFormat.setText(setSlider.toText());
					String value = "" + v + "%";
					tfMin.setText(value);
					
					// Instant update template text
					main.mainController.updateTemplateText();
					
					main.mainController.markChanged();
				}
			};
			
			mclMax = new MyChangeListener() {
				@Override
				protected void doChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
					int v = newValue.intValue();
					int vMin = (int) sldMin.getValue();
					if (v < vMin) {
						v = vMin;
						sldMax.setValue(v);
					}
					setSlider.setPctMax(v);
					
					tfBgFormat.setText(setSlider.toText());
					String value = "" + v + "%";
					tfMax.setText(value);
					
					// Instant update template text
					main.mainController.updateTemplateText();
					
					main.mainController.markChanged();
				}
			};
			
			// For when sliding
			sldMin.valueProperty().addListener(mclMin);
			sldMax.valueProperty().addListener(mclMax);
			
			// For just clicking instead of dragging
			/*sldMin.setOnMouseReleased(e -> {
				if (!sldMin.isValueChanging())
					main.mainController.updateTemplateText();
			});
			sldMax.setOnMouseReleased(e -> {
				if (!sldMax.isValueChanging())
					main.mainController.updateTemplateText();
			});*/
			
			// For after dragging, update text area information
			/*sldMin.valueChangingProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> observable, Boolean wasChanging, Boolean changing) {
					if (!changing)
						main.mainController.updateTemplateText();
				}
			});
			sldMax.valueChangingProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> observable, Boolean wasChanging, Boolean changing) {
					if (!changing)
						main.mainController.updateTemplateText();
				}
			});*/
			
			// For when using the arrow keys for sliding and also, consume other keys,
			// to prevent it from clashing with the scroll pane
			sldMin.addEventFilter(KeyEvent.ANY, new EventHandler<KeyEvent>() {
				@Override
				public void handle(KeyEvent keyEvent) {
					if (keyEvent.getCode() == KeyCode.LEFT || keyEvent.getCode() == KeyCode.RIGHT) {
						main.mainController.updateTemplateText();
					} else {
						if (keyEvent.getCode() == KeyCode.PAGE_DOWN || keyEvent.getCode() == KeyCode.PAGE_UP
						|| keyEvent.getCode() == KeyCode.HOME || keyEvent.getCode() == KeyCode.END)
							keyEvent.consume();
					}
				}
			});
			sldMax.addEventFilter(KeyEvent.ANY, new EventHandler<KeyEvent>() {
				@Override
				public void handle(KeyEvent keyEvent) {
					if (keyEvent.getCode() == KeyCode.LEFT || keyEvent.getCode() == KeyCode.RIGHT) {
						main.mainController.updateTemplateText();
					} else {
						if (keyEvent.getCode() == KeyCode.PAGE_DOWN || keyEvent.getCode() == KeyCode.PAGE_UP
						|| keyEvent.getCode() == KeyCode.HOME || keyEvent.getCode() == KeyCode.END)
							keyEvent.consume();
					}
				}
			});
			
			cbEnabled.setSelected(setSlider.isEnabled());
			cbEnabled.selectedProperty().addListener(new ChangeListener<Boolean>() {
				@Override
				public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
					if (setSlider.isEnabled() != cbEnabled.isSelected()) {
						setSlider.setEnabled(cbEnabled.isSelected());
						main.mainController.updateTemplateText();
						main.mainController.markChanged();
					}
				}
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private abstract class MyChangeListener implements ChangeListener<Number> {
		public boolean active = true;
		
		public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
			if (active)
				doChanged(observable, oldValue, newValue);
		}
		
		protected abstract void doChanged(ObservableValue<? extends Number> observable, Number oldValue, Number newValue);
	}
	
	public void setAllSliderValue(int value) {
		mclMin.active = false;
		mclMax.active = false;

		sldMin.setValue(value);
		sldMax.setValue(value);

		setSlider.setPctMin(value);
		setSlider.setPctMax(value);

		tfBgFormat.setText(setSlider.toText());

		String v = "" + value + "%";
		tfMin.setText(v);

		v = "" + value + "%";
		tfMax.setText(v);

		main.mainController.updateTemplateText();

		mclMin.active = true;
		mclMax.active = true;
		
		main.mainController.markChanged();
	}

	public void setAllMinSliderValue(int value) {
		mclMin.active = false;

		int vMax = (int) sldMax.getValue();
		if (value > vMax) {
			value = vMax;
		}
		sldMin.setValue(value);

		setSlider.setPctMin(value);

		tfBgFormat.setText(setSlider.toText());

		String v = "" + value + "%";
		tfMin.setText(v);

		main.mainController.updateTemplateText();

		mclMin.active = true;
		
		main.mainController.markChanged();
	}

	public void setAllMaxSliderValue(int value) {
		mclMax.active = false;

		int vMin = (int) sldMin.getValue();
		if (value < vMin) {
			value = vMin;
		}
		sldMax.setValue(value);

		setSlider.setPctMax(value);

		tfBgFormat.setText(setSlider.toText());

		String v = "" + value + "%";
		tfMax.setText(v);

		main.mainController.updateTemplateText();

		mclMax.active = true;
		
		main.mainController.markChanged();
	}
}