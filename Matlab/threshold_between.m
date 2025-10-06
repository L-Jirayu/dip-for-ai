function threshold_between(inName, T1, T2)
initGPU;

if nargin < 1 || isempty(inName), inName = 'equalized.png'; end
if nargin < 2 || isempty(T1),     T1     = 85;             end
if nargin < 3 || isempty(T2),     T2     = 170;            end
T1 = max(0, min(255, round(T1)));
T2 = max(0, min(255, round(T2)));
if T1 >= T2, T1 = max(0, T2-1); end

% ====== โฟลเดอร์ผลลัพธ์ ======
outDir  = 'threshold_result/threshold_double';
gpDir   = fullfile(outDir,'geoprops');
graphDir= 'graph/threshold';
if ~exist(outDir,'dir'),   mkdir(outDir); end
if ~exist(gpDir,'dir'),    mkdir(gpDir);  end
if ~exist(graphDir,'dir'), mkdir(graphDir); end

% ====== อ่านรูป & แปลงเป็น uint8 ======
img = imread(inName);
img = toGrayU8(img);
[I,J] = size(img); N = I*J;
fprintf('Input: %s  Size: [%d, %d]  T1=%d  T2=%d\n', inName, I, J, T1, T2);
fprintf("Rule: white=255 for T1 <= gray <= T2 (inclusive)\n");

% ====== Histogram ======
h = histcounts(double(img), 0:256);

% ====== Band-pass (IN): T1 <= x <= T2 => white ======
maskIn      = (img >= uint8(T1)) & (img <= uint8(T2));
betweenOne  = uint8(zeros(I,J)); betweenOne(maskIn) = 255;
countIn     = nnz(maskIn); countOut = N - countIn;
imwrite(betweenOne, fullfile(outDir,'between_1.png'));

% ====== Band-stop (OUT): outside => white ======
betweenZero = uint8(zeros(I,J)); betweenZero(~maskIn) = 255;
imwrite(betweenZero, fullfile(outDir,'between_0.png'));

% ====== GeoProps ======
[whiteIn,  ~] = geopros(betweenOne, 128);
[~, outBk]    = geopros(betweenZero,128);
fprintf('[between_1] WHITE(1, T1<=x<=T2) area=%d, centroid=(%.3f, %.3f)\n', whiteIn.area, whiteIn.cx, whiteIn.cy);
fprintf('[between_0] BLACK(0, outside) area=%d, centroid=(%.3f, %.3f)\n', outBk.area, outBk.cx, outBk.cy);

% ====== Report ======
resDir = fullfile(outDir,'txt_result'); if ~exist(resDir,'dir'), mkdir(resDir); end
fid = fopen(fullfile(resDir,'threshold_between_report.txt'),'w');
fprintf(fid,'Input file: %s\n', inName);
fprintf(fid,'Size (I,J): %d, %d\n', I, J);
fprintf(fid,'N (pixels): %d\n', N);
fprintf(fid,'Thresholds (inclusive): T1=%d  T2=%d\n\n', T1, T2);
fprintf(fid,'--- Pixel sizes ---\n');
fprintf(fid,'between_1: In-range white(1)=%d, black(0)=%d\n', countIn, countOut);
fprintf(fid,'between_0: Out-range black(0)=%d\n\n', outBk.area);
fprintf(fid,'=== Geometric Properties on between_1 (T1<=x<=T2, white=1) ===\n');
writeGeo(fid, 'WHITE (1)', whiteIn); fprintf(fid,"\n");
fprintf(fid,'=== Geometric Properties on between_0 (outside, black=0) ===\n');
writeGeo(fid, 'BLACK (0)', outBk);
fclose(fid);

% ====== Annotated Centroid Images ======
oneAnn  = drawCentroidSimple(betweenOne, whiteIn.cx, whiteIn.cy, [0 200 0]);
zeroAnn = drawCentroidSimple(betweenZero,outBk.cx,   outBk.cy,   [220 60 60]);
imwrite(oneAnn,  fullfile(gpDir,'between_1gp.png'));
imwrite(zeroAnn, fullfile(gpDir,'between_0gp.png'));

% ====== Compare Images ======
eq = imread('equalized.png'); eq = toGrayU8(eq);
cmp  = tile3(eq, betweenOne, betweenZero, 16, {'Equalized','Between (T1≤gray≤T2)','Outside (gray<T1 or gray>T2)'});
imwrite(cmp, fullfile(outDir,'compare_eq_between.png'));
cmp2 = tile2(oneAnn, zeroAnn, 16, {'Between (IN) [Centroid]','Outside [Centroid]'});
imwrite(cmp2, fullfile(gpDir,'compare_between_centroids.png'));

% ====== Histogram Graph ======
drawHistDoubleStyled(h, T1, T2, countIn, countOut, fullfile(graphDir,'double_threshold.png'));
end

% ===== helpers (reuse clean version) =====
function g8 = toGrayU8(img)
% toGrayU8 — แปลงภาพให้เป็น grayscale uint8
% รองรับ: RGB truecolor, uint8/uint16, double/single [0..1], logical

if ndims(img)==3
    % ----- Truecolor (RGB) -----
    if isa(img,'uint16')
        f = double(img) / 65535;
    elseif isa(img,'uint8')
        f = double(img) / 255;
    else
        f = double(img);   % double หรือ single
    end
    % แปลงเป็น grayscale
    R = f(:,:,1); G = f(:,:,2); B = f(:,:,3);
    gray = 0.299*R + 0.587*G + 0.114*B;
    gray = max(0,min(1,gray));
    g8 = uint8(round(gray*255));

else
    % ----- Single channel -----
    if isa(img,'uint8')
        g8 = img;
    elseif isa(img,'uint16')
        g8 = uint8(floor(double(img)/65535*255));
    elseif isa(img,'double') || isa(img,'single')
        g8 = uint8(round(max(0,min(1,double(img)))*255));
    elseif islogical(img)
        g8 = uint8(img)*255;
    else
        g8 = uint8(double(img)); % fallback กัน type แปลก
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
    fprintf(fid,'     Centroid: (x=%.3f, y=%.3f)\n', r.cx, r.cy);
    fprintf(fid,'     BBox: [i_min=%d, j_min=%d] .. [i_max=%d, j_max=%d]\n', ...
        r.minI, r.minJ, r.maxI, r.maxJ);
end
end

function rgb = drawCentroidSimple(grayU8, cx, cy, colorRGB)
rgb = repmat(grayU8,[1 1 3]);
if ~isfinite(cx) || ~isfinite(cy), return; end
x = round(cx); y = round(cy);
[h,w,~] = size(rgb); r = 5; lw = 2;
for dx=-8:8
    xx=x+dx; if xx>=1 && xx<=w && y>=1 && y<=h, for c=1:3, rgb(y,xx,c) = uint8(colorRGB(c)); end, end
end
for dy=-8:8
    yy=y+dy; if yy>=1 && yy<=h && x>=1 && x<=w, for c=1:3, rgb(yy,x,c) = uint8(colorRGB(c)); end, end
end
theta = linspace(0,2*pi,80);
xx = round(x + r*cos(theta)); yy = round(y + r*sin(theta));
for k=1:numel(xx)
    if xx(k)>=1 && xx(k)<=w && yy(k)>=1 && yy(k)<=h
        for t=-floor(lw/2):floor(lw/2)
            y2 = yy(k)+t; x2 = xx(k);
            if y2>=1 && y2<=h, for c=1:3, rgb(y2,x2,c) = uint8(colorRGB(c)); end, end
        end
    end
end
end

function drawHistDoubleStyled(h, T1, T2, inCount, outCount, outPath)
fig = figure('Visible','off');
bar(0:255, double(h), 1); hold on;
xline(double(T1), 'r', 'LineWidth', 1.5);
xline(double(T2), 'r', 'LineWidth', 1.5);
xlim([0 255]); xlabel('Gray'); ylabel('Count');
title('Histogram (0..255) with Double Threshold');

yl = ylim;

% --- Size text ---
text(128, yl(2)*0.96, sprintf('Size — In(1): %d   Out(0): %d', ...
    inCount, outCount), 'HorizontalAlignment','center', 'Color',[.2 .2 .2]);

% --- Labels under the axis ---
ypos = yl(1) - 0.05*(yl(2)-yl(1));   % เลื่อนลงต่ำกว่าแกน X ประมาณ 5%
text(5,   ypos, '0 = Black (outside)', 'FontWeight','bold', 'VerticalAlignment','top');
text(160, ypos, '1 = White (T1≤x≤T2)', 'FontWeight','bold', 'VerticalAlignment','top');

set(gca,'LooseInset',get(gca,'TightInset'));
print(fig, outPath, '-dpng', '-r150'); close(fig);
end


function out = tile3(left, mid, right, gap, ~)
left  = toRGB(left); mid = toRGB(mid); right = toRGB(right);
h = max([size(left,1), size(mid,1), size(right,1)]);
w = size(left,2) + gap + size(mid,2) + gap + size(right,2);
out = uint8(zeros(h, w, 3));
x = 1; [out, x] = paste(out, left,  x, 1); x = x + gap;
[out, x] = paste(out, mid,   x, 1); x = x + gap;
[out, ~] = paste(out, right, x, 1);
end

function out = tile2(left, right, gap, ~)
left  = toRGB(left); right = toRGB(right);
h = max([size(left,1), size(right,1)]);
w = size(left,2) + gap + size(right,2);
out = uint8(zeros(h, w, 3));
x = 1; [out, x] = paste(out, left,  x, 1); x = x + gap;
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
