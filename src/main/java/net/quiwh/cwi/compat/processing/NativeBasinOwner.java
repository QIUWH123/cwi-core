package net.quiwh.cwi.compat.processing;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import java.util.Optional;
import net.minecraft.world.item.crafting.Recipe;
public interface NativeBasinOwner {
    Optional<BasinBlockEntity> cwi$basin();
    Recipe<?> cwi$recipe();
    void cwi$recipe(Recipe<?> recipe);
    void cwi$completed();
}
