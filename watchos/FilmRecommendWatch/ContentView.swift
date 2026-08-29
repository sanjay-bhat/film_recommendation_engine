import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = WatchViewModel()

    var body: some View {
        WatchBrowseView(viewModel: viewModel)
            .task { viewModel.loadData() }
    }
}
