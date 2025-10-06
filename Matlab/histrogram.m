% test.m
% อ่านภาพ, แปลงเป็น Gray (แบบ Java), คำนวณ size, histogram, PDF, CDF,
% ทำ Histogram Equalization, พิมพ์ผล, และวาดกราฟ (บันทึกลงโฟลเดอร์ graph/ และ result/)

clearvars -except filename; clc;
initGPU;

if ~exist('filename','var') || isempty(filename)
    filename = 'picture.png';
end
%filename = 'picture.jpg';

% --- อ่านภาพ (รองรับ indexed/truecolor/grayscale และ 8/16-bit) ---
[Ix, map] = imread(filename);      % map=[] ถ้าไม่ใช่ indexed

to8 = @(A16) uint8(floor(double(A16) / 65535 * 255));  % 16-bit -> 8-bit

if ~isempty(map)
    idx = double(Ix);
    if min(idx(:)) == 0, idx = idx + 1; end
    R = uint8(floor( reshape(map(idx(:),1), size(Ix)) * 255 ));
    G = uint8(floor( reshape(map(idx(:),2), size(Ix)) * 255 ));
    B = uint8(floor( reshape(map(idx(:),3), size(Ix)) * 255 ));
elseif ndims(Ix) == 3
    if isa(Ix,'uint16')
        R = to8(Ix(:,:,1)); G = to8(Ix(:,:,2)); B = to8(Ix(:,:,3));
    else
        R = Ix(:,:,1); G = Ix(:,:,2); B = Ix(:,:,3);
    end
else
    if isa(Ix,'uint16')
        Gray = to8(Ix);
    else
        Gray = Ix;
    end
end

if ~exist('Gray','var')
    Gray = uint8(floor(0.299*double(R) + 0.587*double(G) + 0.114*double(B)));
end

[rows, cols] = size(Gray);
fprintf('I[i,j] = size(I) = [%d, %d]\n', rows, cols);
fprintf('All pixel size = %d\n', rows * cols);

% --- Histogram ---
h = zeros(256,1,'uint32');
T = 0;
for u = 1:rows
    for v = 1:cols
        g = Gray(u,v);
        idx = double(g) + 1;
        h(idx) = h(idx) + 1;
        T = T + double(g);
    end
end

meanI = T / double(rows*cols);
fprintf('T = %.0f\n', T);
fprintf('mean(I) = %.12f\n', meanI);

% === PDF / CDF ===
N = rows * cols;
pdf = double(h) / double(N);
cdf = cumsum(pdf);

% === Histogram Equalization mapping ===
mapEq = uint8( round(255 * cdf) );

eq = zeros(rows, cols, 'uint8');
for u = 1:rows
    for v = 1:cols
        g = Gray(u,v);
        eq(u,v) = mapEq(double(g)+1);
    end
end
imwrite(eq, 'equalized.png');
fprintf('Saved: equalized.png\n');

% === ฮิสโตแกรมของภาพ equalized ===
hEq = zeros(256,1,'uint32');
for i = 0:255
    hEq( double(mapEq(i+1)) + 1 ) = hEq( double(mapEq(i+1)) + 1 ) + h(i+1);
end

fprintf('\nHistogram h_eq(i) [equalized]:\n');
for i = 0:255
    fprintf('%d : %u\n', i, hEq(i+1));
end

% ====== สร้างโฟลเดอร์ result และ graph ======
if ~exist('histrogram_result','dir'), mkdir('histrogram_result'); end
if ~exist('graph/histrogram','dir'), mkdir('graph/histrogram'); end

% ====== เขียนไฟล์ .txt ======
fid = fopen(fullfile('histrogram_result','histogram.txt'),'w');
for i = 0:255
    fprintf(fid, '%d : %u\n', i, h(i+1));
end
fclose(fid);

fid = fopen(fullfile('histrogram_result','pdf.txt'),'w');
for i = 0:255
    fprintf(fid, '%d : %.9f\n', i, pdf(i+1));
end
fclose(fid);

fid = fopen(fullfile('histrogram_result','cdf.txt'),'w');
for i = 0:255
    fprintf(fid, '%d : %.9f\n', i, cdf(i+1));
end
fclose(fid);

fid = fopen(fullfile('histrogram_result','histogram_equalized.txt'),'w');
for i = 0:255
    fprintf(fid, '%d : %u\n', i, hEq(i+1));
end
fclose(fid);

% =================== วาดกราฟ ===================
yMax = max([max(h), max(hEq)]);
if yMax == 0, yMax = 1; end
yMaxD = double(yMax);

fig = figure('visible','off');
bar(0:255, double(h), 1);
xlim([0 255]); ylim([0 yMaxD]);
xlabel('Gray level (0–255)'); ylabel('Count');
title('Histogram (Original)');
print(fig, fullfile('graph/histrogram','histogram.png'), '-dpng', '-r150');
close(fig);

fig = figure('visible','off');
bar(0:255, double(hEq), 1);
xlim([0 255]); ylim([0 yMaxD]);
xlabel('Gray level (0–255)'); ylabel('Count');
title('Histogram (Equalized)');
print(fig, fullfile('graph/histrogram','histogram_equalized.png'), '-dpng', '-r150');
close(fig);

fig = figure('visible','off');
plot(0:255, cdf, '-','LineWidth',2);
xlim([0 255]); ylim([0 1]);
xlabel('Gray level (0–255)'); ylabel('CDF');
title('CDF (0–255)');
grid on;
print(fig, fullfile('graph/histrogram','cdf.png'), '-dpng', '-r150');
close(fig);

fprintf('\nSaved .txt to folder: histrogram_result/\n');
fprintf('Saved graphs to folder: graph/histrogram/\n');


% --- Picture ---
%figure; imshow(imread('picture.jpg'));       title('Original');
%figure; imshow(imread('equalized.png'));     title('Equalized');
%figure; imshow(imread('graph/histogram.png'));          title('Histogram (Original)');
%figure; imshow(imread('graph/histogram_equalized.png'));title('Histogram (Equalized)');
%figure; imshow(imread('graph/cdf.png'));                title('CDF');


