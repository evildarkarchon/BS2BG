{
	Export female NPC list for jBodySlide2BodyGen.
}

Unit ExportScripts;

Var 
  NPCList : TStringList;
  ModNameList : TStringList;
  strFileName : string;

  // Called when the script starts
Function Initialize : integer;

Var i : integer;
Begin
  NPCList := TStringList.Create;

  ModNameList := TStringList.Create;
  ModNameList.Sorted := True;
  ModNameList.Duplicates := dupIgnore;

  strFileName := 'FemaleNPCList';

  AddMessage('Exporting female NPCs to ' + strFileName + ' - [Masters].txt...');
End;

// Called for each selected record in the TES5Edit tree

// If an entire plugin is selected then all records in the plugin will be processed
Function Process(e : IInterface) : integer;

Var i : integer;
  strTemp, strMod, strName, strEditorId, strRace, strFormId : string;
Begin
  If Signature(e) <> 'NPC_' Then exit;
  If Not ElementExists(GetElementEditValues(e, 'RNAM'),
     'RACE \ DATA - Data \ Flags \ Playable') Then exit;


  If Not ElementExists(e, 'ACBS - Configuration\Flags\Is CharGen Face Preset')
     And ElementExists(e, 'ACBS - Configuration\Flags\Female')
    Then
    Begin
      strMod := '' + GetFileName(GetFile(e));
      strName := GetElementEditValues(e, 'FULL');
      strEditorId := GetElementEditValues(e, 'EDID');
      strRace := '' + GetElementEditValues(e, 'RNAM');
      strFormId := '' + IntToHex(FixedFormID(e), 8);

      strTemp := strMod + ' | ' + strName + ' | ' + strEditorId + ' | ' +
                 strRace + ' | ' + strFormId;
      NPCList.Add(strTemp);

      ModNameList.Add(strMod);

      AddMessage(strTemp);
    End;
End;

// Called after the script has finished processing every record
Function Finalize : integer;

Var i : integer;
  strMods : string;
Begin
  strMods := '';

  For i := 0 To ModNameList.Count - 1 Do
    Begin
      If (i <= 0) Then
        Begin
          strMods := ModNameList[i];
          strMods := stringreplace(strMods, '.esm', '', [rfReplaceAll,
                     rfIgnoreCase]);
          strMods := stringreplace(strMods, '.esp', '', [rfReplaceAll,
                     rfIgnoreCase]);
        End
      Else
        Begin
          strMods := strMods + ' + ' + ModNameList[i];
          strMods := stringreplace(strMods, '.esm', '', [rfReplaceAll,
                     rfIgnoreCase]);
          strMods := stringreplace(strMods, '.esp', '', [rfReplaceAll,
                     rfIgnoreCase]);
        End;
    End;


  strFileName := strFileName + ' - ' + strMods + '.txt';
  NPCList.SaveToFile(strFileName);
  AddMessage(Format('Exported %d NPCs to file %s.', [NPCList.Count, strFileName]
  ));

  ModNameList.Free;
  NPCList.Free;
End;

End.
