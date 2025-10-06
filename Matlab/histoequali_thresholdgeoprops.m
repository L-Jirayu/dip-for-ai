function histoequali_thresholdgeoprops(input, T, T1, T2)
initGPU;

% -------- defaults --------
if nargin < 2 || isempty(T),  T  = 128; end
if nargin < 3 || isempty(T1), T1 = 85;  end
if nargin < 4 || isempty(T2), T2 = 170; end

% -------- lock to folder of THIS function --------
thisDir = fileparts(mfilename('fullpath'));
origDir = pwd; c = onCleanup(@() cd(origDir));
cd(thisDir);

% -------- resolve input path (รับ absolute/relative) --------
if nargin < 1 || isempty(input)
    [f,p] = uigetfile({'*.png;*.jpg;*.jpeg;*.bmp;*.tif','Images'}, 'Select image');
    assert(~isequal(f,0), 'ไม่ได้เลือกไฟล์ภาพ');
    input = fullfile(p,f);
else
    input = char(strtrim(string(input)));
end
assert(exist(input,'file')==2, 'ไม่พบไฟล์ภาพ: %s', input);

% ================== STEP 1: Histogram + Equalization ==================
fprintf('== Step 1: histrogram ==\n');
scriptPath = fullfile(thisDir,'histrogram.m');
assert(exist(scriptPath,'file')==2, 'ไม่พบ histrogram.m ใน %s', thisDir);

assignin('base','filename',input);                     % ส่งตัวแปรเข้า base
evalin('base', sprintf('run(''%s'');', scriptPath));   % รันสคริปต์ที่ base

% หา equalized.png ที่เพิ่งสร้าง (อยู่ใน thisDir)
eqPath = fullfile(thisDir,'equalized.png');
if ~isfile(eqPath)
    d = dir(fullfile(thisDir,'equalized.*'));
    assert(~isempty(d), 'ไม่พบ equalized.png หลังจบ STEP 1');
    eqPath = fullfile(thisDir, d(1).name);
end

% ================== STEP 2: Single threshold ==================
fprintf('\n== Step 2: threshold (single) ==\n');
assert(exist('threshold_single.m','file')==2, 'ไม่พบ threshold_single.m ใน %s', thisDir);
threshold_single(eqPath, T);

% ================== STEP 3: Double threshold ==================
fprintf('\n== Step 3: threshold_between (double) ==\n');
assert(exist('threshold_between.m','file')==2, 'ไม่พบ threshold_between.m ใน %s', thisDir);
threshold_between(eqPath, T1, T2);

% ================== STEP 4: Image Moment (บนผล single-threshold) ==================
% เรียกแบบไม่ส่งอาร์กิวเมนต์ เพื่อให้ image_moment ใช้ดีฟอลต์:
% "threshold_result/threshold_single/threshold_1.png"
fprintf('\n== Step 4: image_moment (on single-threshold result) ==\n');
assert(exist('image_moment.m','file')==2, 'ไม่พบ image_moment.m ใน %s', thisDir);
image_moment();  % <-- ดีฟอลต์ path เหมือนฝั่ง Java

fprintf('\nAll done.\nOutputs:\n');
fprintf('- equalized.png (from histrogram)\n');
fprintf('- threshold_result/... (single & double)\n');
fprintf('- graph/... (histogram & threshold graphs)\n');
fprintf('- image_moment/picture_output/{raw_moment.png, central_moment.png, hu_moment.png}\n');
fprintf('- image_moment/txt_output/moment_report.txt\n');
end
