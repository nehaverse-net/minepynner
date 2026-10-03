package net.nehaverse.pynner.debug;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class DebugScreen extends Screen {
  public DebugScreen() {
    super(Text.literal("Pynner Debug"));
  }

  @Override
  protected void init() {
    addDrawableChild(
        ButtonWidget.builder(
                Text.literal("Reload Python"),
                button -> {
                  if (client != null
                      && client.getNetworkHandler() != null
                      && DebugClient.instance.isDebugConnection())
                    client.getNetworkHandler().sendChatCommand("pynner reload");
                })
            .dimensions(width / 2 - 155, height - 30, 150, 20)
            .build());
    addDrawableChild(
        ButtonWidget.builder(Text.literal("Close"), button -> close())
            .dimensions(width / 2 + 5, height - 30, 150, 20)
            .build());
  }

  @Override
  public boolean shouldPause() {
    return false;
  }

  @Override
  public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
    super.render(draw, mouseX, mouseY, delta);
    draw.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
    var session = DebugClient.instance.session;
    String[] fields = {"state", "project", "directory", "runtime", "error"};
    int y = 35;
    for (String field : fields) {
      String line = field + ": " + DebugClient.string(session, field, "-");
      draw.drawTextWithShadow(
          textRenderer, textRenderer.trimToWidth(line, width - 32), 16, y, 0xFFE0E0E0);
      y += 13;
    }
    if (session.has("logs") && session.get("logs").isJsonArray()) {
      var lines = session.getAsJsonArray("logs");
      int count = Math.max(0, (height - y - 45) / 11);
      for (int i = Math.max(0, lines.size() - count); i < lines.size(); i++) {
        draw.drawTextWithShadow(
            textRenderer,
            textRenderer.trimToWidth(lines.get(i).getAsString(), width - 32),
            16,
            y,
            0xFFB0B0B0);
        y += 11;
      }
    }
  }
}
