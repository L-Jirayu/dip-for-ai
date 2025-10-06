function threshold_single(inName, T)
initGPU;

if nargin < 1 || isempty(inName), inName = 'equalized.png'; end
if nargin < 2 || isempty(T),      T      = 128;             end
T = max(0, min(255, round(T)));

% ====== โฟลเดอร์ผลลัพธ์ ======
outDir  = 'threshold_result/threshold_single';
gpDir   = fullfile(outDir,'geoprops');
graphDir= 'graph/threshold';
if ~exist(outDir,'dir'),   mkdir(outDir); end
if ~exist(gpDir,'dir'),    mkdir(gpDir);  end
if ~exist(graphDir,'dir'), mkdir(graphDir); end

% ====== อ่านรูป & แปลงเป็น uint8 แบบ no-toolbox ======
img = imread(inName);
img = toGrayU8(img);                 % <-- แทน rgb2gray/im2uint8
[I,J] = size(img); N = I*J;
fprintf('Input: %s  Size: [%d, %d]  T=%d\n', inName, I, J, T);

% ====== Histogram (no-toolbox) ======
h = histcounts(double(img), 0:256);  % 256 bins, edges 0..256

% ====== Threshold: gray>=T -> 255, else 0 (threshold_1) ======
maskW   = img >= uint8(T);
thrOne  = uint8(zeros(I,J));  thrOne(maskW)  = 255;
countWhite = nnz(maskW); countBlack = N - countWhite;
imwrite(thrOne, fullfile(outDir,'threshold_1.png'));

% ====== Threshold (inverted): gray<T -> 255, else 0 (threshold_0) ======
thrZero = uint8(zeros(I,J));  thrZero(~maskW) = 255;
imwrite(thrZero, fullfile(outDir,'threshold_0.png'));

% ====== GeoProps (ของคุณเอง) ======
[oneWhite, ~] = geopros(thrOne, 128);     % WHITE(1) = gray>=T
[~, zeroBk]   = geopros(thrZero,128);     % BLACK(0) = gray<T
fprintf('[threshold_1] WHITE(1) area=%d, centroid=(%.3f, %.3f)\n', oneWhite.area, oneWhite.cx, oneWhite.cy);
fprintf('[threshold_0] BLACK(0) area=%d, centroid=(%.3f, %.3f)\n', zeroBk.area, zeroBk.cx, zeroBk.cy);

% ====== รายงาน .txt ======
resDir = fullfile(outDir,'txt_result'); if ~exist(resDir,'dir'), mkdir(resDir); end
fid = fopen(fullfile(resDir,'threshold_report.txt'),'w');
fprintf(fid,'Input file: %s\n', inName);
fprintf(fid,'Size (I,J): %d, %d\n', I, J);
fprintf(fid,'N (pixels): %d\n', N);
fprintf(fid,'Threshold T: %d\n\n', T);
fprintf(fid,"--- Pixel sizes ---\n");
fprintf(fid,"threshold_1: white(1)=%d\n", countWhite);
fprintf(fid,"threshold_0: black(0)=%d\n\n", countBlack);
fprintf(fid,"=== Geometric Properties on threshold_1 (1=white=gray>=T) ===\n");
writeGeo(fid,'WHITE (1)', oneWhite); fprintf(fid,"\n");
fprintf(fid,"=== Geometric Properties on threshold_0 (0=black=gray<T) ===\n");
writeGeo(fid,'BLACK (0)', zeroBk);
fclose(fid);

% ====== วาด centroid (no-toolbox overlay) ======
thrOneAnn  = drawCentroidSimple(thrOne,  oneWhite.cx, oneWhite.cy, [0 200 0]);
thrZeroAnn = drawCentroidSimple(thrZero, zeroBk.cx,   zeroBk.cy,   [220 60 60]);
imwrite(thrOneAnn,  fullfile(gpDir,'thereshold_1gp.png'));
imwrite(thrZeroAnn, fullfile(gpDir,'thereshold_0gp.png'));

% ====== รวมรูป (Equalized + 2 Threshold) ======
eq = imread('equalized.png'); eq = toGrayU8(eq);
cmp = tile3(eq, thrOne, thrZero, 16, {'Equalized','Thr (>=T → WHITE=1)','Thr (<T → WHITE=1) / Report BLACK(0)'});
imwrite(cmp, fullfile(outDir,'compare_eq_thr.png'));

cmp2 = tile2(thrOneAnn, thrZeroAnn, 16, {'Thr (>=T → WHITE=1)  [Centroid WHITE(1)]', ...
                                         'Thr (<T → WHITE=1)  [Report BLACK(0) Centroid]'});
imwrite(cmp2, fullfile(gpDir,'compare_thr_centroids.png'));

% ====== กราฟฮิสโตแกรม + เส้น Threshold ======
drawHistSingleStyled(h, T, countWhite, countBlack, fullfile(graphDir,'single_threshold.png'));
fprintf('Saved Graph in: %s\n', fullfile(graphDir,'single_threshold.png'));
fprintf('Saved: %s\n', fullfile(gpDir,'thereshold_1gp.png'));
fprintf('Saved: %s\n', fullfile(gpDir,'thereshold_0gp.png'));
end

function g8 = toGrayU8(img)
% toGrayU8 — แปลงภาพเป็น uint8 grayscale
% รองรับ: RGB truecolor, uint8/uint16, double/single [0..1], logical

if ndims(img) == 3
    % ----- Truecolor (RGB) -----
    if isa(img,'uint16')
        f = double(img) / 65535;
    elseif isa(img,'uint8')
        f = double(img) / 255;
    else
        f = double(img);   % double/single
    end
    % แปลงเป็น grayscale
    R = f(:,:,1); G = f(:,:,2); B = f(:,:,3);
    gray = 0.299*R + 0.587*G + 0.114*B;
    gray = max(0, min(1, gray));       % clamp [0,1]
    g8 = uint8(round(gray*255));

else
    % ----- Single channel (Grayscale) -----
    if isa(img,'uint8')
        g8 = img;
    elseif isa(img,'uint16')
        g8 = uint8(floor(double(img) / 65535 * 255));
    elseif isa(img,'double') || isa(img,'single')
        g8 = uint8(round(max(0, min(1, double(img))) * 255));
    elseif islogical(img)
        g8 = uint8(img) * 255;
    else
        % fallback กัน type แปลก
        g8 = uint8(double(img));
    end
end
end

function writeGeo(fid, title, r)
fprintf(fid,'  -- %s --\n', title);
if r.area==0
    fprintf(fid,'     Area: 0 (no object)\n');
    fprintf(fid,'     Centroid: N/A\n');
    fprintf(fid,'     Bounding Box: N/A\n');
else
    fprintf(fid,'     Area: %d pixels\n', r.area);
    fprintf(fid,'     Centroid: (x=%.3f, y=%.3f)  [x=column(j), y=row(i)]\n', r.cx, r.cy);
    fprintf(fid,'     BBox (rows i, cols j): [i_min=%d, j_min=%d] .. [i_max=%d, j_max=%d]\n', ...
        r.minI, r.minJ, r.maxI, r.maxJ);
end
end

function rgb = drawCentroidSimple(grayU8, cx, cy, colorRGB)
% วาด crosshair + วงกลมเล็ก ด้วยการแก้พิกเซลตรง ๆ (ไม่ใช้ insertShape/insertText)
rgb = repmat(grayU8,[1 1 3]);
if ~isfinite(cx) || ~isfinite(cy), return; end
x = round(cx); y = round(cy);
[h,w,~] = size(rgb);
r = 5;  lw = 2;

% เส้น crosshair
for dx=-8:8
    xx = x+dx; 
    if xx>=1 && xx<=w && y>=1 && y<=h
        for c=1:3, rgb(y,xx,c) = uint8(colorRGB(c)); end
    end
end
for dy=-8:8
    yy = y+dy; 
    if yy>=1 && yy<=h && x>=1 && x<=w
        for c=1:3, rgb(yy,x,c) = uint8(colorRGB(c)); end
    end
end
% วงกลม
theta = linspace(0,2*pi,80);
xx = round(x + r*cos(theta)); yy = round(y + r*sin(theta));
for k=1:numel(xx)
    if xx(k)>=1 && xx(k)<=w && yy(k)>=1 && yy(k)<=h
        for t=-floor(lw/2):floor(lw/2)
            y2 = yy(k)+t; x2 = xx(k);
            if y2>=1 && y2<=h
                for c=1:3, rgb(y2,x2,c) = uint8(colorRGB(c)); end
            end
        end
    end
end
end

function drawHistSingleStyled(h, T, whiteCount, blackCount, outPath)
fig = figure('Visible','off');
bar(0:255, double(h), 1); hold on;
xline(double(T), 'r', 'LineWidth', 1.5);
xlim([0 255]); xlabel('Gray'); ylabel('Count');
title('Histogram (0..255) with Threshold');

yl = ylim;

% --- Size text ---
text(128, yl(2)*0.96, sprintf('Size — White(1): %d   Black(0): %d', ...
    whiteCount, blackCount), 'HorizontalAlignment','center', 'Color',[.2 .2 .2]);

% --- Labels under the axis ---
ypos = yl(1) - 0.05*(yl(2)-yl(1));   % เลื่อนลงใต้กรอบนิดหน่อย
text(5,   ypos, '0 = Black', 'FontWeight','bold', 'VerticalAlignment','top');
text(190, ypos, '1 = White', 'FontWeight','bold', 'VerticalAlignment','top');

set(gca,'LooseInset',get(gca,'TightInset'));
print(fig, outPath, '-dpng', '-r150'); close(fig);
end


function out = tile3(left, mid, right, gap, ~)
left  = toRGB(left); mid = toRGB(mid); right = toRGB(right);
h = max([size(left,1), size(mid,1), size(right,1)]);
w = size(left,2) + gap + size(mid,2) + gap + size(right,2);
out = uint8(zeros(h, w, 3));
x = 1;
[out, x] = paste(out, left,  x, 1); x = x + gap;
[out, x] = paste(out, mid,   x, 1); x = x + gap;
[out, ~] = paste(out, right, x, 1);
% (ไม่เขียนตัวหนังสือลงรูปเพื่อเลี่ยง toolbox)
end

function out = tile2(left, right, gap, ~)
left  = toRGB(left); right = toRGB(right);
h = max([size(left,1), size(right,1)]);
w = size(left,2) + gap + size(right,2);
out = uint8(zeros(h, w, 3));
x = 1;
[out, x] = paste(out, left,  x, 1); x = x + gap;
[out, ~] = paste(out, right, x, 1);
end

function [dst, x2] = paste(dst, src, x, y)
[h, w, ~] = size(src);
dst(y:y+h-1, x:x+w-1, :) = src;
x2 = x + w;
end

function rgb = toRGB(img)
if size(img,3)==1, rgb = repmat(img,[1 1 3]); else, rgb = img; end
end
