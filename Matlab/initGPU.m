function initGPU()
    if gpuDeviceCount > 0
        d = gpuDevice(1);
        fprintf("✅ GPU Enabled: %s (Memory: %g GB)\n", d.Name, d.TotalMemory/1e9);
    else
        fprintf("⚠️ No GPU detected, using CPU.\n");
    end
end
