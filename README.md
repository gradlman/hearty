# hearty
Source code for the publication "Real-time ECG monitoring and arrhythmia detection using Android-based mobile devices" (DOI: 10.1109/EMBC.2012.6346460)

This Release Package contains supplementary material related to the publication [1]:

[1] S. Gradl, P. Kugler, C. Lohmüller, and B. Eskofier, "Real-time ECG monitoring and arrhythmia detection using Android-based mobile devices," in 34th Annual International Conference of the IEEE EMBS, 2012, pp. 2452-2455.

Licensed under the Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License (the "License"); you may not use any of the files contained in this package except in compliance with the License. To view a copy of this License, visit http://creativecommons.org/licenses/by-nc-sa/3.0/.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
specific language governing permissions and limitations under the License.


## Restrictions
----------------------------
This package contains the original Android application project filestructure that was created using the Google Android SDK and the Eclipe IDE.
As part of this package, only those source code files are released that were not already licensed differently. 
Most of the core functionality described in [1] is included in this release.





## General Notes
----------------
For the MIT-BIH database parsing, you need to install the relevant database
files to the application folder on your Android device:
/mnt/sdcard/Android/data/de.lme.hearty/files/[db filename]

For each record, a signal file and an annotation file have to be supplied.
For example, to include MIT-BIH Arrhythmia DB record #100 and MIT-BIH
Supraventricular Arrhythmia DB record #804, supply the following files:

.../files/mitdb100ann.csv
.../files/mitdb100sig.csv
.../files/mitdb804ann.csv
.../files/mitdb804sig.csv.gz

These must be created from original database records using the following
commands/PhysioNET utilities:

> rdsamp -r [db record numer] -c -H -f 0 -v -pS > mitdb[db record numer]sig.csv;
> rdann -r [db record numer] -f 0 -a atr -v > mitdb[db record numer]ann.csv;
(> gzip -9 mitdb[db record numer]sig.csv;)


## Usage
------------------
To quickstart, just run the application, open the menu and press "Test Record".

To use any of the MIT-BIH records, supply the desired record files, go to the
menu, select "Settings..." -> "Data Source" and choose the supplied record number.
Afterwards, enter the "Column" number "2" or "3", depending on which ECG lead should
be used. "2" means the first lead available, "3" means the second lead available.
As "Multiplier" "1000" should be used for every MIT-BIH record.

Using "SimCount" you may restrict the number of simulated samples. Choosing "0"
simulates all available samples.

In the upper area of the Settings, you may select which plot should be displayed in
the main screen.

After closing the settings activity by pressing the "Back" button on your device,
you can start simulation by selecting "Connect" in the menu.
The data files will now be loaded. Depending on the record this might take up to one
minute (for records containing up to 650,000 entries)!

At any time during simulation, you may select "Disconnect" in the menu to stop
simulation and display the statistics.


## Troubleshooting
------------------
If you have problems with Hearty:

Force close the application using any task killer app. Restart Hearty.
