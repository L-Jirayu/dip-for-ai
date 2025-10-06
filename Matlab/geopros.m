function [white, black] = geopros(img, thr)
% img: uint8 (0..255) หรือ logical ก็ได้, thr: ค่า threshold สำหรับแบ่ง 0/1 (แนะนำ 128)
% คืนค่า: struct white, black แต่ละอันมี field: area, cx, cy, minI, minJ, maxI, maxJ
% หมายเหตุ: cx = คอลัมน์(j), cy = แถว(i), index แบบ MATLAB (เริ่มที่ 1)

initGPU;
if ~isa(img,'uint8')
    if islogical(img)
        img = uint8(img) * 255;
    else
        img = im2uint8(img);
    end
end
maskW = img >= uint8(thr);
white = measureMask(maskW);
black = measureMask(~maskW);
end

function r = measureMask(mask)
area = nnz(mask);
if area == 0
    r = struct('area',0,'cx',NaN,'cy',NaN,'minI',-1,'minJ',-1,'maxI',-1,'maxJ',-1);
    return;
end
[rows, cols] = find(mask);           % rows = i, cols = j
r.area = area;
r.cx   = mean(cols);                  % centroid x = j
r.cy   = mean(rows);                  % centroid y = i
r.minI = min(rows);  r.maxI = max(rows);
r.minJ = min(cols);  r.maxJ = max(cols);
end
