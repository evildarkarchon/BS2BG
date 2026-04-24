package com.asdasfa.jbs2bg;

import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

import com.asdasfa.jbs2bg.data.SliderPreset;
import com.asdasfa.jbs2bg.data.SliderPreset.SetSlider;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * 
 * @author Totiman
 */
public class PopupSetSlidersController extends CustomController {
	
	@FXML
	private ScrollPane spSetSlidersList;
	@FXML
	private VBox vbSetSlidersList;
	
	@FXML
	private CheckBox cbAll;
	@FXML
	private CheckBox cbAllMin;
	@FXML
	private CheckBox cbAllMax;
	
	@FXML
	private TextField tfAll;
	@FXML
	private TextField tfAllMin;
	@FXML
	private TextField tfAllMax;
	
	@FXML
	private Slider sldAll;
	@FXML
	private Slider sldAllMin;
	@FXML
	private Slider sldAllMax;
	
	@FXML
	private Button btnBack;
	
	public final ArrayList<SetSliderControl> setSliderControls = new ArrayList<SetSliderControl>();
	
	public PopupSetSlidersController() {
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
	}
	
	@Override
	protected void onPostInit() {
		double scrollSpeedMult = 2;
		vbSetSlidersList.setOnScroll(e -> {
			double deltaY = e.getDeltaY() * scrollSpeedMult;
			double height = spSetSlidersList.getContent().getBoundsInLocal().getHeight();
			double vvalue = spSetSlidersList.getVvalue();
			spSetSlidersList.setVvalue(vvalue + -deltaY/height);
		});
		
		stage.setOnShown(e -> {
			onShown();
		});
		stage.setOnHidden(e -> {
			setSliderControls.clear();
			vbSetSlidersList.getChildren().clear();
		});
		
		// Checkbox All
		cbAll.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				SliderPreset preset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
				if (preset == null)
					return;
				
				int v = (int) sldAll.getValue();
				
				boolean useAll = cbAll.isSelected();
				// Enable slider and text fields if cbAll is selected
				sldAll.setDisable(!useAll);
				tfAll.setText(v + "%");
				tfAll.setDisable(!useAll);
				
				if (useAll) { // If selected, uncheck both min and max sliders
					cbAllMin.setSelected(false);
					cbAllMax.setSelected(false);

					sldAllMin.setDisable(true);
					sldAllMin.setValue(v);
					tfAllMin.setText(v + "%");
					tfAllMin.setDisable(true);

					sldAllMax.setDisable(true);
					sldAllMax.setValue(v);
					tfAllMax.setText(v + "%");
					tfAllMax.setDisable(true);
				}
				
				// Enable/Disable all SetSliderControls and override their values with the one in sldAll
				for (int i = 0; i < setSliderControls.size(); i++) {
					SetSliderControl setSliderControl = setSliderControls.get(i);
					setSliderControl.setAllSliderValue(v);
					setSliderControl.setDisable(useAll);
				}
			}
		});
		// Checkbox All Min
		cbAllMin.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				SliderPreset preset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
				if (preset == null)
					return;
				
				int v = (int) sldAllMin.getValue();
				
				boolean useAllMin = cbAllMin.isSelected();
				// Enable slider and text fields if cbAllMin is selected
				sldAllMin.setDisable(!useAllMin);
				tfAllMin.setText(v + "%");
				tfAllMin.setDisable(!useAllMin);
				
				if (useAllMin) { // If selected, uncheck "all" sliders
					cbAll.setSelected(false);
					
					sldAll.setDisable(true);
					tfAll.setText((int) sldAll.getValue() + "%");
					tfAll.setDisable(true);
				}
				
				// Enable/Disable all SetSliderControls and override their min values with the one in sldAllMin
				for (int i = 0; i < setSliderControls.size(); i++) {
					SetSliderControl setSliderControl = setSliderControls.get(i);
					setSliderControl.setAllMinSliderValue(v);
					if (!cbAllMax.isSelected())
						setSliderControl.setDisable(useAllMin);
				}
			}
		});
		// Checkbox All Max
		cbAllMax.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
			public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				SliderPreset preset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
				if (preset == null)
					return;
				
				int v = (int) sldAllMax.getValue();
				
				boolean useAllMax = cbAllMax.isSelected();
				// Enable slider and text fields if cbAllMax is selected
				sldAllMax.setDisable(!useAllMax);
				tfAllMax.setText(v + "%");
				tfAllMax.setDisable(!useAllMax);
				
				if (useAllMax) { // If selected, uncheck "all" sliders
					cbAll.setSelected(false);
					
					sldAll.setDisable(true);
					tfAll.setText((int) sldAll.getValue() + "%");
					tfAll.setDisable(true);
				}
				
				// Enable/Disable all SetSliderControls and override their min values with the one in sldAllMin
				for (int i = 0; i < setSliderControls.size(); i++) {
					SetSliderControl setSliderControl = setSliderControls.get(i);
					setSliderControl.setAllMaxSliderValue(v);
					if (!cbAllMin.isSelected())
						setSliderControl.setDisable(useAllMax);
				}
			}
		});
		
		// Slider All
		sldAll.valueProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				int v = newValue.intValue();
				if (!cbAll.isSelected()) {
					tfAll.setText(v + "%");
					return;
				}
				
				tfAll.setText(v + "%");
				
				for (int i = 0; i < setSliderControls.size(); i++) {
					SetSliderControl setSliderControl = setSliderControls.get(i);
					setSliderControl.setAllSliderValue(v);
				}
				
				sldAllMin.setValue(v);
				sldAllMax.setValue(v);
			}
		});
		// Slider All Min
		sldAllMin.valueProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				int v = newValue.intValue();
				if (!cbAllMin.isSelected()) {
					tfAllMin.setText(v + "%");
					return;
				}
				
				int vMax = (int) sldAllMax.getValue();
				if (v > vMax) {
					v = vMax;
					sldAllMin.setValue(v);
				}
				tfAllMin.setText(v + "%");
				
				for (int i = 0; i < setSliderControls.size(); i++) {
					SetSliderControl setSliderControl = setSliderControls.get(i);
					setSliderControl.setAllMinSliderValue(v);
				}
			}
		});
		// Slider All Max
		sldAllMax.valueProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				int v = newValue.intValue();
				if (!cbAllMax.isSelected()) {
					tfAllMax.setText(v + "%");
					return;
				}
				
				int vMin = (int) sldAllMin.getValue();
				if (v < vMin) {
					v = vMin;
					sldAllMax.setValue(v);
				}
				tfAllMax.setText(v + "%");
				
				for (int i = 0; i < setSliderControls.size(); i++) {
					SetSliderControl setSliderControl = setSliderControls.get(i);
					setSliderControl.setAllMaxSliderValue(v);
				}
			}
		});
	}
	
	private void onShown() {
		SliderPreset preset = main.mainController.lvPresets.getSelectionModel().getSelectedItem();
		if (preset == null)
			return;
		
		stage.setTitle("SetSliders: " + preset.getName());
		btnBack.requestFocus();
		
		int defaultValue = 100;
		
		cbAll.setSelected(false);
		tfAll.setText(defaultValue + "%");
		tfAll.setDisable(true);
		sldAll.setDisable(true);
		sldAll.setValue(defaultValue);

		cbAllMin.setSelected(false);
		tfAllMin.setText(defaultValue + "%");
		tfAllMin.setDisable(true);
		sldAllMin.setDisable(true);
		sldAllMin.setValue(defaultValue);

		cbAllMax.setSelected(false);
		tfAllMax.setText(defaultValue + "%");
		tfAllMax.setDisable(true);
		sldAllMax.setDisable(true);
		sldAllMax.setValue(defaultValue);
		
		setSliderControls.clear();
		vbSetSlidersList.getChildren().clear();
		
		ArrayList<SetSlider> allSliders = preset.getAllSetSliders();
		for (int i = 0; i < allSliders.size(); i++) {
			SetSlider setSlider = allSliders.get(i);
			SetSliderControl setSliderControl = new SetSliderControl(main, setSlider);
			
			vbSetSlidersList.getChildren().add(setSliderControl);
			setSliderControls.add(setSliderControl);
		}
	}
	
	@FXML
	private void hide() {
		stage.hide();
	}
	
	@FXML
	private void zeroAll() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAll.setValue(0);
		sldAllMin.setValue(0);
		sldAllMax.setValue(0);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllSliderValue(0);
		}
	}
	
	@FXML
	private void fiftyAll() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAll.setValue(50);
		sldAllMin.setValue(50);
		sldAllMax.setValue(50);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllSliderValue(50);
		}
	}
	
	@FXML
	private void hundredAll() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAll.setValue(100);
		sldAllMin.setValue(100);
		sldAllMax.setValue(100);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllSliderValue(100);
		}
	}
	
	@FXML
	private void zeroAllMin() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAllMin.setValue(0);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllMinSliderValue(0);
		}
	}
	
	@FXML
	private void fiftyAllMin() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAllMin.setValue(50);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllMinSliderValue(50);
		}
	}
	
	@FXML
	private void hundredAllMin() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAllMin.setValue(100);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllMinSliderValue(100);
		}
	}
	
	@FXML
	private void zeroAllMax() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAllMax.setValue(0);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllMaxSliderValue(0);
		}
	}
	
	@FXML
	private void fiftyAllMax() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAllMax.setValue(50);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllMaxSliderValue(50);
		}
	}
	
	@FXML
	private void hundredAllMax() {
		if (main.mainController.lvPresets.getSelectionModel().getSelectedItem() == null)
			return;
		
		sldAllMax.setValue(100);
		
		for (int i = 0; i < setSliderControls.size(); i++) {
			SetSliderControl setSliderControl = setSliderControls.get(i);
			setSliderControl.setAllMaxSliderValue(100);
		}
	}
}